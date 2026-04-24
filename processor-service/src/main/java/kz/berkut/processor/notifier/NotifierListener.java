package kz.berkut.processor.notifier;

import kz.berkut.common.Topics;
import kz.berkut.common.VehicleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotifierListener {
    private static final Logger log = LoggerFactory.getLogger(NotifierListener.class);

    private final WebhookNotifier notifier;

    public NotifierListener(WebhookNotifier notifier) {
        this.notifier = notifier;
    }

    @KafkaListener(topics = Topics.EVENTS_CRITICAL, containerFactory = "notifierFactory")
    public void onCritical(VehicleEvent event) {
        log.info("CRITICAL received device={} type={} eventId={}",
                event.deviceId(), event.type(), event.eventId());
        notifier.post(event);
    }
}
