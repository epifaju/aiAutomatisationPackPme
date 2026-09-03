package com.aipack.email;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class CapturingOutboundMailSender implements OutboundMailSender {

    public static final List<OutboundMail> SENT = new CopyOnWriteArrayList<>();

    public static void reset() {
        SENT.clear();
    }

    @Override
    public void send(OutboundMail mail) {
        SENT.add(mail);
    }
}
