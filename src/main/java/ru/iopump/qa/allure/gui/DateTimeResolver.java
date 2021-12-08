package ru.iopump.qa.allure.gui;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.annotation.VaadinSessionScope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.iopump.qa.allure.properties.AllureProperties;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;

@Slf4j
@Component
@VaadinSessionScope
public class DateTimeResolver {
    private final DateTimeFormatter serverFormatter;
    private final Object lock = new Object();
    private final AtomicBoolean flaky = new AtomicBoolean();
    private final String pattern;

    private DateTimeFormatter clientFormatter;
    private Runnable clientReady;

    public DateTimeResolver(AllureProperties allureProperties) {
        this.pattern = allureProperties.dateFormat();
        this.serverFormatter = DateTimeFormatter.ofPattern(allureProperties.dateFormat()).withZone(serverZone());
    }

    public static ZoneId serverZone() {
        var c = Calendar.getInstance();
        return c.getTimeZone().toZoneId();
    }

    public static ZoneId zeroZone() {
        return ZoneOffset.UTC;
    }

    public void retrieve() {
        UI.getCurrent().getPage()
            .retrieveExtendedClientDetails(extendedClientDetails -> {
                if (extendedClientDetails == null) {
                    log.warn("Cannot retrieve client details");
                    return;
                }
                String timeZoneId = extendedClientDetails.getTimeZoneId();
                log.info("Client timezone = {}", timeZoneId);
                synchronized (lock) {
                    try {
                        var zoneId = ZoneId.of(timeZoneId);
                        clientFormatter = serverFormatter.withZone(zoneId);
                        fireReady();
                    } catch (DateTimeException e) {
                        if (log.isErrorEnabled()) {
                            log.error(
                                format("Unknown timezone '%s'. Server timezone '%s' will be used", timeZoneId, serverFormatter.getZone()),
                                e);
                        }
                        clientFormatter = serverFormatter;
                    }
                }
            });
    }

    public DateTimeFormatter acquireFormatter() {
        synchronized (lock) {
            if (clientFormatter != null) {
                log.debug("Acquire client date time format '{}'", info(clientFormatter));
                return clientFormatter;
            } else {
                log.warn("Cannot get client side timezone. Acquire server date time format '{}'", info(serverFormatter));
                flaky.set(true);
                return serverFormatter;
            }
        }
    }

    public void onClientReady(Runnable runnable) {
        this.clientReady = runnable;
    }

    public String printDate(LocalDateTime localDateTime) {
        LocalDateTime ldt = localDateTime;
        if (localDateTime == null) {
            ldt = LocalDateTime.MIN;
        }
        var formatter = acquireFormatter();
        return formatter.format(ldt.atZone(zeroZone()));
    }

    private void fireReady() {
        if (flaky.compareAndSet(true, false)
            && Objects.equals(clientFormatter, serverFormatter)
            && clientReady != null) {

            log.info("Update date format from '{}' to '{}'", info(serverFormatter), info(clientFormatter));
            clientReady.run();
        }
    }

    private String info(DateTimeFormatter formatter) {
        var timeZone = TimeZone.getTimeZone(formatter.getZone());
        var h = timeZone.getOffset(Calendar.ZONE_OFFSET) / 1000 / 60 / 60;
        var sign = h > 0 ? "+" : "-";
        return pattern + " " + formatter.getZone() + " " + sign + h;
    }
}
