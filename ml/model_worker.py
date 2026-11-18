"""
WareManager machine-learning worker (Python with scikit-learn).

It trains the F1 Demand forecasting model: a least-squares straight line (linear regression), used for
  - the length of the winter:      winter days = a + b x October temperature
  - sales of winter products:      weekly sales = base + extra x cold days in the week

How it fits into the MVC app
  The Spring Boot app (PythonModelTrainer.java, in the Model/service layer) starts this script once
  and keeps it running. The app writes one request per line (JSON) to the script's standard input,
  and the script writes one answer per line (JSON) to its standard output. There is no web server
  here: the Java MVC app handles every page and every database access; this script only trains the
  model. If Python is not installed, the app uses its Java copy of the same formula
  (JavaModelTrainer.java), so the app always works and the numbers are the same.

Request and answer (one line each)
  {"task": "fit_line", "x": [...], "y": [...]}
      -> {"ok": true, "intercept": a, "slope": b, "r2": 0.94, "residual_std": 2.1, "points": 8}
  anything wrong -> {"ok": false, "error": "..."}

Set up once:   pip install -r ml/requirements.txt          (Windows: py -m pip install -r ml\\requirements.txt)
Try it alone:  python ml/model_worker.py   then type   {"task": "fit_line", "x": [1, 2, 3], "y": [2, 4, 6]}
"""
import json
import math
import platform
import sys

try:
    import numpy as np
    import sklearn
    from sklearn.linear_model import LinearRegression
except ImportError as error:
    # Tell the Java app why we cannot start; it then uses its Java backup.
    print(json.dumps({"ready": False, "error": "%s (run: pip install -r ml/requirements.txt)" % error}), flush=True)
    sys.exit(1)

def round_to(value, factor):
    """Rounds exactly like the Java backup (Math.floor(value * factor + 0.5) / factor),
    so Python and Java give identical numbers. factor 1e6 = 6 decimals."""
    return math.floor(value * factor + 0.5) / factor


def fit_line(x, y):
    """F1: trains the straight line y = intercept + slope * x (least squares) with scikit-learn."""
    n = len(x)
    if n != len(y):
        raise ValueError("x and y must have the same length")
    if n == 0:
        return {"intercept": 0.0, "slope": 0.0, "r2": 0.0, "residual_std": 0.0, "points": 0}

    features = np.array(x, dtype=float).reshape(-1, 1)  # one row per point, one column (x)
    targets = np.array(y, dtype=float)
    model = LinearRegression().fit(features, targets)    # the machine-learning step

    # How good is the line? R2 = 1 means perfect, 0 means no better than the average.
    predicted = model.predict(features)
    residual_error = float(np.sum((targets - predicted) ** 2))
    total_error = float(np.sum((targets - targets.mean()) ** 2))
    r2 = 0.0 if total_error == 0 else 1 - residual_error / total_error
    residual_std = math.sqrt(residual_error / (n - 2)) if n > 2 else 0.0  # typical prediction error

    return {"intercept": round_to(float(model.intercept_), 1e6),
            "slope": round_to(float(model.coef_[0]), 1e6),
            "r2": round_to(r2, 1e6),
            "residual_std": round_to(residual_std, 1e6),
            "points": n}


def answer(request):
    task = request.get("task")
    if task == "fit_line":
        return fit_line(request["x"], request["y"])
    raise ValueError("unknown task: %s" % task)


def main():
    # First line: tell the Java app we are ready and which versions are used (shown on the F1 page).
    print(json.dumps({"ready": True, "python": platform.python_version(), "sklearn": sklearn.__version__}), flush=True)
    for line in sys.stdin:  # one request per line until the Java app closes the pipe
        line = line.strip()
        if not line:
            continue
        try:
            result = answer(json.loads(line))
            result["ok"] = True
        except Exception as error:  # a bad request must not stop the worker
            result = {"ok": False, "error": str(error)}
        print(json.dumps(result), flush=True)


if __name__ == "__main__":
    main()
