package dev.haller.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import dev.haller.dashboard.config.AppProperties;
import dev.haller.dashboard.model.WifiData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

@Service
public class WifiService {

    private static final Logger log = LoggerFactory.getLogger(WifiService.class);

    private final AppProperties props;
    private final SettingsService settingsService;
    private final ObjectMapper objectMapper;

    public WifiService(AppProperties props, SettingsService settingsService, ObjectMapper objectMapper) {
        this.props = props;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    private WifiData buildWifiData() throws WriterException, IOException {
        String ssid = props.getWifi().getSsid();
        String password = props.getWifi().getPassword();
        String wifiString = "WIFI:T:WPA;S:" + ssid + ";P:" + password + ";;";

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(wifiString, BarcodeFormat.QR_CODE, 200, 200);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", stream);
        String base64 = Base64.getEncoder().encodeToString(stream.toByteArray());

        return new WifiData(ssid, password, "data:image/png;base64," + base64);
    }

    public Flux<ServerSentEvent<String>> eventFlux() {
        if (!settingsService.isEnabled("wifi")) return Flux.empty();
        return Mono.fromCallable(this::buildWifiData)
            .map(data -> {
                try {
                    return ServerSentEvent.<String>builder()
                        .event("wifi")
                        .data(objectMapper.writeValueAsString(data))
                        .build();
                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
            })
            .flux()
            .onErrorResume(e -> {
                log.error("WiFi data error: {}", e.getMessage());
                return Flux.empty();
            });
    }
}
