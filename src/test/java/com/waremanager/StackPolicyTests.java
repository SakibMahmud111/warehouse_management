package com.waremanager;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the CSE470 technology restrictions:
 *   - MERN (MongoDB, Express, React, Node) or any combination of it is prohibited.
 *   - Django, Flask, FastAPI and every other Python web framework are prohibited.
 *   - Spring MVC with Thymeleaf views must remain the application framework.
 *
 * It scans the files in the repository (not only src/), so it also catches a framework
 * added in a side folder. Runs with `mvn test` and on GitHub Actions.
 * If it fails, remove the prohibited technology; do not weaken this test.
 */
class StackPolicyTests {

    /** Maven and IntelliJ run tests from the project folder (the one with pom.xml). */
    private static final Path ROOT = Paths.get("").toAbsolutePath();

    private static final Set<String> SKIPPED_FOLDERS =
            Set.of(".git", "target", ".idea", ".vscode", "node_modules", ".venv", "venv", "__pycache__");

    /** Python web frameworks and the servers that run them. */
    private static final String PYTHON_WEB =
            "flask|django|fastapi|starlette|uvicorn|gunicorn|streamlit|gradio|tornado|bottle|pyramid"
            + "|aiohttp|sanic|quart|falcon|cherrypy|web2py";

    static final Pattern PYTHON_WEB_IMPORT =
            Pattern.compile("(?m)^\\s*(from|import)\\s+(" + PYTHON_WEB + ")\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern PYTHON_WEB_DEPENDENCY =
            Pattern.compile("\\b(" + PYTHON_WEB + ")\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern MERN_NPM_DEPENDENCY =
            Pattern.compile("\"(express|react|react-dom|next|mongodb|mongoose)\"\\s*:");
    static final Pattern MERN_JS_IMPORT =
            Pattern.compile("(require\\(\\s*|from\\s+)['\"](express|react|react-dom|mongodb|mongoose)['\"]");
    static final Pattern REACT_SCRIPT_TAG =
            Pattern.compile("<script[^>]+src=[\"'][^\"']*react", Pattern.CASE_INSENSITIVE);
    static final Pattern MONGO_IN_BUILD_FILE =
            Pattern.compile("mongo", Pattern.CASE_INSENSITIVE);

    @Test
    void noMernAndNoPythonWebFrameworks() throws IOException {
        List<String> problems = new ArrayList<>();

        for (Path file : repositoryFiles()) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            String where = ROOT.relativize(file).toString();

            if (name.equals("manage.py")) {
                problems.add(where + ": Django project file");
            }
            if (name.endsWith(".py") && PYTHON_WEB_IMPORT.matcher(read(file)).find()) {
                problems.add(where + ": imports a Python web framework");
            }
            if (isPythonDependencyFile(name) && PYTHON_WEB_DEPENDENCY.matcher(read(file)).find()) {
                problems.add(where + ": lists a Python web framework");
            }
            if (name.equals("package.json") && MERN_NPM_DEPENDENCY.matcher(read(file)).find()) {
                problems.add(where + ": depends on part of the MERN stack");
            }
            if (isJavaScriptFile(name) && MERN_JS_IMPORT.matcher(read(file)).find()) {
                problems.add(where + ": imports part of the MERN stack");
            }
            if (name.endsWith(".html") && REACT_SCRIPT_TAG.matcher(read(file)).find()) {
                problems.add(where + ": loads React");
            }
            if (isBuildFile(name) && MONGO_IN_BUILD_FILE.matcher(read(file)).find()) {
                problems.add(where + ": uses MongoDB (the M in MERN)");
            }
        }

        assertTrue(problems.isEmpty(), "Prohibited technology found:\n" + String.join("\n", problems));
    }

    @Test
    void springMvcWithThymeleafViewsStaysInPlace() throws IOException {
        String pom = read(ROOT.resolve("pom.xml"));
        assertTrue(pom.contains("<artifactId>spring-boot-starter-web</artifactId>"),
                "Spring MVC (spring-boot-starter-web) must remain the web framework");
        assertTrue(pom.contains("<artifactId>spring-boot-starter-thymeleaf</artifactId>"),
                "Thymeleaf (the View layer) must remain in place");

        try (Stream<Path> views = Files.list(ROOT.resolve("src/main/resources/templates"))) {
            assertTrue(views.anyMatch(p -> p.toString().endsWith(".html")),
                    "src/main/resources/templates must contain the Thymeleaf views");
        }
    }

    /** Proves the guard really detects the things it is meant to block. */
    @Test
    void guardRecognisesProhibitedExamples() {
        assertTrue(PYTHON_WEB_IMPORT.matcher("from flask import Flask").find());
        assertTrue(PYTHON_WEB_IMPORT.matcher("import django").find());
        assertTrue(PYTHON_WEB_IMPORT.matcher("from fastapi import FastAPI").find());
        assertTrue(PYTHON_WEB_DEPENDENCY.matcher("fastapi==0.110.0").find());
        assertTrue(MERN_NPM_DEPENDENCY.matcher("{ \"dependencies\": { \"express\": \"^4.19.0\" } }").find());
        assertTrue(MERN_JS_IMPORT.matcher("const mongoose = require('mongoose');").find());
        assertTrue(MERN_JS_IMPORT.matcher("import React from \"react\";").find());
        assertTrue(REACT_SCRIPT_TAG.matcher("<script src=\"https://unpkg.com/react@18/umd/react.production.min.js\"></script>").find());
    }

    private static List<Path> repositoryFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(ROOT)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> !isInSkippedFolder(p))
                    .toList();
        }
    }

    private static boolean isInSkippedFolder(Path file) {
        for (Path part : ROOT.relativize(file)) {
            if (SKIPPED_FOLDERS.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPythonDependencyFile(String name) {
        return (name.startsWith("requirements") && name.endsWith(".txt"))
                || name.equals("pyproject.toml") || name.equals("pipfile") || name.equals("setup.py")
                || name.equals("setup.cfg") || name.equals("environment.yml") || name.equals("environment.yaml");
    }

    private static boolean isJavaScriptFile(String name) {
        return name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".mjs") || name.endsWith(".cjs")
                || name.endsWith(".ts") || name.endsWith(".tsx");
    }

    private static boolean isBuildFile(String name) {
        return name.equals("pom.xml") || name.endsWith(".gradle") || name.endsWith(".gradle.kts");
    }

    /** Reads a file as text; odd bytes are replaced instead of causing an error. */
    private static String read(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
