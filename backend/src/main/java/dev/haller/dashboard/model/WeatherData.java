package dev.haller.dashboard.model;

import java.util.List;

public record WeatherData(
    CurrentWeather current,
    List<HourlyEntry> hourly,
    List<DailyEntry> daily
) {
    public record CurrentWeather(
        double temperature,
        double apparentTemperature,
        double precipitation,
        int weatherCode,
        double windspeed,
        int relativeHumidity,
        int isDay
    ) {}

    public record HourlyEntry(
        String time,
        double temperature,
        double apparentTemperature,
        int precipitationProbability,
        int weatherCode,
        int isDay
    ) {}

    public record DailyEntry(
        String date,
        int weatherCode,
        double temperatureMax,
        double temperatureMin,
        double precipitationSum,
        int precipitationProbabilityMax
    ) {}
}
