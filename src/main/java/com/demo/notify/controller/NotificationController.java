package com.demo.notify.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/notifications")
@CrossOrigin // prod ortamda bu global configürasyon yapılmalı, burada örnek için ekledik
public class NotificationController {

    // List yerine Map'e geçtik: String key (userId) - SseEmitter value
    private final Map<String,SseEmitter> emitters = new ConcurrentHashMap<>();

    // 1. Abonelik: Client bağlanır ve emitter listesine eklenir
    @GetMapping(
            value = "/subscribe/{userId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE // SSE için zorunlu content-type
    )
    public SseEmitter subscribe(@PathVariable String userId) {

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // bağlantıyı açık tut
        emitters.put(userId, emitter); // userId ile emitter'ı kaydet

        // Bağlantı kapanırsa listeden temizle
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));

        // İlk bağlantı mesajı (opsiyonel ama iyi pratik)
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data(userId + "Bağlantı kuruldu"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    // 2. Yayın: Tüm bağlı clientlara veri gönder
    @PostMapping("/send")
    public void send(@RequestParam String message) {


        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("MESSAGE")
                        .data(message));
            } catch (IOException e) {
               emitters.remove(userId); // Hata varsa emitter'ı listeden çıkar
            }
        });
        }

   // 3. Kişiye Özel Yayın (Unicast): Sadece tek bir kullanıcıya gönderir
   // Demo amaçlı PathVariable kullanıldı (normalde JWT'den alınır)
   @PostMapping("/send-private")
    public void sendToUser(@RequestParam String userId, @RequestParam String message) {
        SseEmitter emitter = emitters.get(userId); // userId'ye göre emitter'ı al
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("PRIVATE_MESSAGE")
                        .data(message));
            } catch (IOException e) {
                emitters.remove(userId); // Hata varsa emitter'ı listeden çıkar
            }
        }
    }
    }
