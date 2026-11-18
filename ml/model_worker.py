"""
WareManager prediction worker (Python with scikit-learn, SciPy and NumPy).

It runs the prediction models of four features:
  F1  Demand forecasting       - linear regression (scikit-learn) for the length of the winter and for
                                 "weekly sales = base + extra x cold days" of winter products
  F6  Lead-time prediction     - weighted average of a supplier's past deliveries, spread and trend
  F7  Stockout probability     - chance that demand before the next delivery is larger than the stock
                                 on hand (normal distribution from SciPy)
  F20 Demand anomaly detection - which recent weeks are far from what the F1 model expected (NumPy)

How it fits into the MVC app
  The Spring Boot app (PythonModelTrainer.java, in the Model/service layer) starts this script once
  and keeps it running. The app writes one request per line (JSON) to the script's standard input,
  and the script writes one answer per line (JSON) to its standard output. There is no web server
  here: the Java MVC app handles every page, every database access and every alert; this script only
  runs the models. If Python is not installed, the app uses its Java copy of the same formulas
  (JavaModelTrainer.java), so the app always works and the numbers are the same.

Requests and answers (one line each)
  {"task": "fit_line", "x": [...], "y": [...]}
      -> {"ok": true, "intercept": a, "slope": b, "r2": 0.94, "residual_std": 2.1, "points": 8}
  {"task": "lead_time", "days": [...]}      past lead times in days, oldest delivery first (3 or more)
      -> {"ok": true, "predicted_days": 13.93, "spread_days": 2.26, "trend_per_delivery": 0.89}
  {"task": "stockout_risk", "stock": 200, "demand": 273.6, "horizon_days": 8,
   "daily_error_std": 14.3, "lead_time_std_days": 2.73}
      -> {"ok": true, "probability": 0.765, "demand_std": 102.9}
  {"task": "anomalies", "actual": [...], "expected": [...], "threshold_percent": 50, "min_units": 5}
      -> {"ok": true, "unusual": [false, true, ...], "deviation_percent": [3.1, 85.4, ...]}
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
    import scipy
    import sklearn
    from scipy.stats import norm
    from sklearn.linear_model import LinearRegression
except ImportError as error:
    # Tell the Java app why we cannot start; it then uses its Java backup.
    print(json.dumps({"ready": False, "error": "%s (run: pip install -r ml/requirements.txt)" % error}), flush=True)
    sys.exit(1)

NEWEST_WEIGHT = 0.3  # F6: each new delivery counts 30% of the prediction


def round_to(value, factor):
    """Rounds exactly like the Java backup (Math.floor(value * factor + 0.5) / factor),
    so Python and Java give identical numbers. factor 100 = 2 decimals, 1e6 = 6 decimals."""
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


def lead_time(days):
    """F6: predicts the next lead time from past lead times (oldest delivery first)."""
    count = len(days)
    if count < 3:
        raise ValueError("at least 3 deliveries are needed for a prediction")

    # Exponentially weighted average: prediction = 0.3 x newest + 0.7 x previous prediction.
    weighted = float(days[0])
    for d in days[1:]:
        weighted = NEWEST_WEIGHT * d + (1 - NEWEST_WEIGHT) * weighted

    # Spread: sample standard deviation of all lead times (at least half a day).
    # Plain loops in the same order as the Java backup, so the result is identical.
    total = 0.0
    for d in days:
        total += d
    mean = total / count
    squares = 0.0
    for d in days:
        squares += (d - mean) * (d - mean)
    spread = max(0.5, math.sqrt(squares / (count - 1)))

    # Trend: slope of a line through the lead times (days per delivery; positive = getting slower).
    trend = fit_line(list(range(count)), days)["slope"]

    return {"predicted_days": round_to(weighted, 100),
            "spread_days": round_to(spread, 100),
            "trend_per_delivery": trend}


def stockout_risk(stock, demand, horizon_days, daily_error_std, lead_time_std_days):
    """F7: chance that demand before the next delivery is larger than the stock on hand.

    Demand until the delivery is treated as normally distributed: mean = the F1 forecast for those days,
    spread = sqrt(days x daily forecast error^2 + daily demand^2 x lead-time spread^2)."""
    daily_demand = demand / horizon_days
    std = math.sqrt(horizon_days * daily_error_std * daily_error_std
                    + daily_demand * daily_demand * lead_time_std_days * lead_time_std_days)
    if demand <= 0:
        probability = 0.0                                  # nothing will be sold: no risk
    elif std <= 0:
        probability = 1.0 if stock < demand else 0.0       # no uncertainty at all
    else:
        probability = float(norm.sf((stock - demand) / std))  # P(demand > stock) from the bell curve
    return {"probability": probability, "demand_std": std}


def anomalies(actual, expected, threshold_percent, min_units):
    """F20: compares each week's actual sales with what the F1 model expected for that week.
    A week is unusual when it is at least threshold_percent and at least min_units away."""
    actual = np.array(actual, dtype=float)
    expected = np.array(expected, dtype=float)
    usable = expected > 0.5                                # no forecast means nothing to compare with
    safe_expected = np.where(usable, expected, 1.0)        # avoids dividing by zero
    difference = np.abs(actual - expected)
    deviation = np.where(usable, (actual - expected) / safe_expected * 100, 0.0)  # +85 = 85% above
    unusual = usable & (difference >= min_units) & (difference / safe_expected * 100 >= threshold_percent)
    return {"unusual": [bool(v) for v in unusual], "deviation_percent": [float(v) for v in deviation]}


def answer(request):
    task = request.get("task")
    if task == "fit_line":
        return fit_line(request["x"], request["y"])
    if task == "lead_time":
        return lead_time(request["days"])
    if task == "stockout_risk":
        return stockout_risk(request["stock"], request["demand"], request["horizon_days"],
                             request["daily_error_std"], request["lead_time_std_days"])
    if task == "anomalies":
        return anomalies(request["actual"], request["expected"], request["threshold_percent"], request["min_units"])
    raise ValueError("unknown task: %s" % task)


def main():
    # First line: tell the Java app we are ready and which versions are used (shown on the F1, F6, F7 and F20 pages).
    print(json.dumps({"ready": True, "python": platform.python_version(), "sklearn": sklearn.__version__,
                      "scipy": scipy.__version__, "numpy": np.__version__}), flush=True)
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
