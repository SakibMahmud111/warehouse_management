package com.waremanager.service;

import com.waremanager.model.DemandForecast;
import com.waremanager.model.Product;

import java.util.List;

/** Everything the F1 page shows for one product: the learned model, last year's sales and the saved forecasts. */
public class ProductForecast {

    private final Product product;
    private final DemandModel model;
    private final List<WeekSales> lastYear;
    private final List<DemandForecast> forecasts;

    public ProductForecast(Product product, DemandModel model, List<WeekSales> lastYear, List<DemandForecast> forecasts) {
        this.product = product;
        this.model = model;
        this.lastYear = lastYear;
        this.forecasts = forecasts;
    }

    public Product getProduct() { return product; }
    public DemandModel getModel() { return model; }
    /** Up to 52 most recent weeks of actual sales, oldest first. */
    public List<WeekSales> getLastYear() { return lastYear; }
    /** The next 12 weeks, as saved by the last planning run. */
    public List<DemandForecast> getForecasts() { return forecasts; }

    /** Total forecast units for the next 12 weeks. */
    public double getForecastTotal() {
        double total = 0;
        for (DemandForecast forecast : forecasts) {
            total += forecast.getQuantity();
        }
        return total;
    }
}
