package com.sastewa.jpgdatesetter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        var fileName = "/home/scott/Downloads/Wedding_0429.jpg";
        var file = new File(fileName);
        var filePath = file.toPath();
        // figureing out the time part
        Clock clockStart = Clock.fixed(Instant.parse("1990-10-20T10:00:00-04:00"),
                ZoneId.of(ZoneId.SHORT_IDS.get("EST")));
        System.out.println(clockStart.instant());
        System.out.println(clockStart);
        long startMillisSinceEpoc = clockStart.millis();
        System.out.println("clockMillis: " + startMillisSinceEpoc);
        Date date = new Date(startMillisSinceEpoc);
        System.out.println("date: " + date);
        LocalDateTime ldt = LocalDateTime.now(clockStart);
        System.out.println("ldt: " + ldt);
        ldt = ldt.plusMinutes(10);
        System.out.println("ldt: " + ldt);
        // figuring out duration
        Duration duration = Duration.parse("PT10M"); // use ISO 8601 format
        System.out.println("duration: " + duration);
        System.out.println("duration: " + duration.toMillis());
        ldt = ldt.plus(duration);
        System.out.println("ldt2: " + ldt);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("YYYY:MM:dd HH:mm:SS");
        System.out.println("ltd3: " + ldt.format(dtf));
        Date newDate = new Date(startMillisSinceEpoc + duration.toMillis());
        System.out.println("date: " + newDate);
        System.exit(0);
    }
}
