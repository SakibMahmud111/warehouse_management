package com.waremanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Runs the prediction models of F1, F6, F7 and F20 in Python ({@code ml/model_worker.py}: scikit-learn,
 * SciPy, NumPy).
 *
 * How it works:
 * <ol>
 *   <li>The first time a model is needed, the Python script is started once and kept running.</li>
 *   <li>Each request is one line of JSON written to the script; the answer is one line of JSON back.</li>
 *   <li>If Python or scikit-learn is missing, or the script stops answering, the same formulas in
 *       {@link JavaModelTrainer} are used instead, so the app always works and gives the same numbers.
 *       The F1, F6, F7 and F20 pages show which one is in use.</li>
 * </ol>
 * No web framework is involved: the script is a child process of this MVC app, not a server.
 *
 * Settings (application.properties): {@code app.ml.python.enabled}, {@code app.ml.python.command}
 * (empty = try python3/python, or py/python/python3 on Windows) and {@code app.ml.python.script}.
 */
@Service
public class PythonModelTrainer implements ModelTrainer {

    private static final Logger log = LoggerFactory.getLogger(PythonModelTrainer.class);
    /** Loading scikit-learn the first time can take a while on a slow PC. */
    private static final long START_TIMEOUT_SECONDS = 60;
    private static final long ANSWER_TIMEOUT_SECONDS = 10;
    /** Put in the queue when Python closes its output (it stopped). */
    private static final String END_OF_OUTPUT = "\u0000end";

    private final boolean enabled;
    private final String command;
    private final String script;
    private final ObjectMapper json = new ObjectMapper();

    private Process python;
    private BufferedWriter toPython;
    private BlockingQueue<String> fromPython;
    private boolean startTried;
    private String status = "Python has not been started yet";

    public PythonModelTrainer(@Value("${app.ml.python.enabled:true}") boolean enabled,
                              @Value("${app.ml.python.command:}") String command,
                              @Value("${app.ml.python.script:ml/model_worker.py}") String script) {
        this.enabled = enabled;
        this.command = command == null ? "" : command.trim();
        this.script = script;
    }

    // ---------------------------------------------------------------- the two models

    @Override
    public synchronized Statistics.LineFit fitLine(double[] x, double[] y) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task", "fit_line");
        request.put("x", x);
        request.put("y", y);
        JsonNode answer = ask(request);
        if (answer == null) {
            return JavaModelTrainer.INSTANCE.fitLine(x, y);
        }
        return new Statistics.LineFit(answer.get("intercept").asDouble(), answer.get("slope").asDouble(),
                answer.get("r2").asDouble(), answer.get("residual_std").asDouble(), answer.get("points").asInt());
    }

    @Override
    public synchronized LeadTimeFit leadTime(double[] leadDaysOldestFirst) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task", "lead_time");
        request.put("days", leadDaysOldestFirst);
        JsonNode answer = ask(request);
        if (answer == null) {
            return JavaModelTrainer.INSTANCE.leadTime(leadDaysOldestFirst);
        }
        return new LeadTimeFit(answer.get("predicted_days").asDouble(), answer.get("spread_days").asDouble(),
                answer.get("trend_per_delivery").asDouble());
    }

    @Override
    public synchronized StockoutRisk stockoutRisk(double stock, double demand, int horizonDays, double dailyErrorStd,
                                                  double leadTimeStdDays) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task", "stockout_risk");
        request.put("stock", stock);
        request.put("demand", demand);
        request.put("horizon_days", horizonDays);
        request.put("daily_error_std", dailyErrorStd);
        request.put("lead_time_std_days", leadTimeStdDays);
        JsonNode answer = ask(request);
        if (answer == null) {
            return JavaModelTrainer.INSTANCE.stockoutRisk(stock, demand, horizonDays, dailyErrorStd, leadTimeStdDays);
        }
        return new StockoutRisk(answer.get("probability").asDouble(), answer.get("demand_std").asDouble());
    }

    @Override
    public synchronized AnomalyCheck anomalies(double[] actual, double[] expected, double thresholdPercent,
                                               double minUnits) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("task", "anomalies");
        request.put("actual", actual);
        request.put("expected", expected);
        request.put("threshold_percent", thresholdPercent);
        request.put("min_units", minUnits);
        JsonNode answer = ask(request);
        if (answer == null || answer.get("unusual").size() != actual.length) {
            return JavaModelTrainer.INSTANCE.anomalies(actual, expected, thresholdPercent, minUnits);
        }
        boolean[] unusual = new boolean[actual.length];
        double[] deviation = new double[actual.length];
        for (int i = 0; i < actual.length; i++) {
            unusual[i] = answer.get("unusual").get(i).asBoolean();
            deviation[i] = answer.get("deviation_percent").get(i).asDouble();
        }
        return new AnomalyCheck(unusual, deviation);
    }

    // ---------------------------------------------------------------- shown on the F1, F6, F7 and F20 pages

    /** True while the models are trained by Python. */
    public synchronized boolean isUsingPython() {
        return start();
    }

    /** Which engine runs the models, for example "Python 3.12.4 with scikit-learn 1.5.1 (ml/model_worker.py)". */
    public synchronized String getStatus() {
        start();
        return status;
    }

    // ---------------------------------------------------------------- talking to Python

    /** Sends one request. Returns the answer, or null when Python is not available (then Java is used). */
    private JsonNode ask(Map<String, Object> request) {
        if (!start()) {
            return null;
        }
        try {
            toPython.write(json.writeValueAsString(request));
            toPython.write("\n");
            toPython.flush();
            String line = fromPython.poll(ANSWER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (line == null || END_OF_OUTPUT.equals(line)) {
                throw new IOException(line == null ? "no answer within " + ANSWER_TIMEOUT_SECONDS + " s" : "Python stopped");
            }
            JsonNode answer = json.readTree(line);
            if (!answer.path("ok").asBoolean()) {
                // Python could not handle this one request: use Java for it, keep Python for the rest.
                log.warn("Python model worker refused a request ({}); the Java backup was used for it", answer.path("error").asText());
                return null;
            }
            return answer;
        } catch (IOException ex) {
            stop("Python stopped answering (" + ex.getMessage() + ")");
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Starts Python the first time it is needed. Returns true if Python is running. */
    private boolean start() {
        if (python != null) {
            return true;
        }
        if (startTried) {
            return false;   // tried once already and fell back to Java; restart the app to try again
        }
        startTried = true;
        if (!enabled) {
            useJava("switched off (app.ml.python.enabled=false)");
            return false;
        }
        Path scriptPath = Path.of(script).toAbsolutePath();
        if (!Files.isRegularFile(scriptPath)) {
            useJava(script + " not found (start the app from the project folder)");
            return false;
        }
        List<String> problems = new ArrayList<>();
        for (List<String> pythonCommand : pythonCommands()) {
            String problem = tryStart(pythonCommand, scriptPath);
            if (problem == null) {
                return true;
            }
            problems.add(String.join(" ", pythonCommand) + ": " + problem);
        }
        useJava(String.join("; ", problems));
        return false;
    }

    /** Starts the script with one Python command and waits for its "ready" line. Returns null on success, else why not. */
    private String tryStart(List<String> pythonCommand, Path scriptPath) {
        List<String> full = new ArrayList<>(pythonCommand);
        full.add("-u");                       // unbuffered: every answer is sent at once
        full.add(scriptPath.toString());
        Process process;
        try {
            process = new ProcessBuilder(full).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException ex) {
            return "not installed";
        }
        BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        Thread reader = new Thread(() -> readLines(process, lines), "python-model-reader");
        reader.setDaemon(true);
        reader.start();
        try {
            String first = lines.poll(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (first == null || END_OF_OUTPUT.equals(first)) {
                process.destroyForcibly();
                return first == null ? "no answer within " + START_TIMEOUT_SECONDS + " s" : "stopped without answering";
            }
            JsonNode hello = json.readTree(first);
            if (!hello.path("ready").asBoolean()) {
                process.destroyForcibly();
                return hello.path("error").asText("not ready");
            }
            python = process;
            toPython = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            fromPython = lines;
            status = "Python " + hello.path("python").asText() + " with scikit-learn " + hello.path("sklearn").asText()
                    + ", SciPy " + hello.path("scipy").asText() + ", NumPy " + hello.path("numpy").asText() + " (" + script + ")";
            log.info("Prediction models (F1, F6, F7, F20) run in {}", status);
            return null;
        } catch (IOException ex) {
            process.destroyForcibly();
            return "unexpected answer (" + ex.getMessage() + ")";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return "interrupted";
        }
    }

    /** Runs in its own thread: passes every line Python prints to the queue. */
    private static void readLines(Process process, BlockingQueue<String> lines) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException ignored) {
            // the process was stopped
        }
        lines.add(END_OF_OUTPUT);
    }

    /** The Python commands to try, in order. */
    private List<List<String>> pythonCommands() {
        if (!command.isEmpty()) {
            if (isFile(command)) {
                return List.of(List.of(command));   // a full path, which may contain spaces
            }
            return List.of(Arrays.asList(command.split("\\s+")));   // for example "py -3"
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        return windows
                ? List.of(List.of("py", "-3"), List.of("python"), List.of("python3"))
                : List.of(List.of("python3"), List.of("python"));
    }

    private static boolean isFile(String path) {
        try {
            return Files.isRegularFile(Path.of(path));
        } catch (RuntimeException ex) {
            return false;   // not a valid path on this system
        }
    }

    private void stop(String reason) {
        if (python != null) {
            python.destroyForcibly();
        }
        python = null;
        toPython = null;
        useJava(reason);
    }

    private void useJava(String reason) {
        status = "Java backup (Python not used: " + reason + ")";
        log.warn("Prediction models (F1, F6, F7, F20): {}. Install Python 3 and run 'pip install -r ml/requirements.txt', then restart.", status);
    }

    /** Called by Spring when the app stops: closing the input ends the Python loop. */
    @PreDestroy
    public synchronized void close() {
        if (python != null) {
            try {
                toPython.close();
            } catch (IOException ignored) {
                // already closed
            }
            python.destroy();
            python = null;
        }
    }
}
