package com.demo.notify.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/notifications")
@CrossOrigin // prod ortamda bu global configürasyon yapılmalı, burada örnek için ekledik
public class NotificationController {

    // Thread-safe liste: Aynı anda hem ekleme hem gönderim güvenli
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    // 1. Abonelik: Client bağlanır ve emitter listesine eklenir
    @GetMapping(
            value = "/subscribe",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE // SSE için zorunlu content-type
    )
    public SseEmitter subscribe() {

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // bağlantıyı açık tut
        emitters.add(emitter);

        // Bağlantı kapanırsa listeden temizle
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));

        // İlk bağlantı mesajı (opsiyonel ama iyi pratik)
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("Bağlantı kuruldu"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    // 2. Yayın: Tüm bağlı clientlara veri gönder
    @PostMapping("/send")
    public void send(@RequestParam String message) {

        List<SseEmitter> deadEmitters = new CopyOnWriteArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(message)); // istersen JSON da gönderebiliriz
            } catch (IOException e) {
                deadEmitters.add(emitter); // direkt silmek yerine işaretle
            }
        }

        emitters.removeAll(deadEmitters); // temizleme
    }
}