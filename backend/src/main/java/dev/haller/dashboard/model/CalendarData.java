package dev.haller.dashboard.model;

import java.util.List;
import java.util.Map;

public record CalendarData(Map<String, List<String>> wasteTypes, List<String> mrgreen) {}
