package com.cameo.commander;


import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
@RestController
@RequestMapping(path = "/commands")
@ConfigurationProperties(prefix = "commander")
public class HomeController {

    @Setter
    private Map<String, String> commands;
    private final Map<String, LocalDateTime> lastExecution = new HashMap<>();

    @PostConstruct
    public void init() {
        log.info("Commands: " + commands);
    }

    @GetMapping(path = "/{command}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamFlux(@PathVariable String command) {
        String s = commands.get(command);
        if (s == null)
            return Flux.just("Command not found");

        return Flux.create((FluxSink<String> sink) -> {

                    LocalDateTime localDateTime = lastExecution.get(command);
                    if (localDateTime != null) {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM-dd HH:mm");
                        sink.next(command + ": previously executed " + formatter.format(localDateTime));
                    }
                    lastExecution.put(command, LocalDateTime.now());

                    execute(s,
                            sink::next,
                            finishCode -> {
                                sink.next(command + " FINISHED : " + finishCode);
                                lastExecution.put(command, LocalDateTime.now());
                                sink.complete();
                            });
                }
        ).doOnNext(s2->log.info(s2));
    }


    @SneakyThrows
    private void execute(String command, Consumer<String> consumer, Consumer<Integer> finish) {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");

        log.info("Executing command "+command);
        ProcessBuilder builder = new ProcessBuilder();
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("bash", "-c", command);
        }
       // builder.directory(new File(System.getProperty("user.home")));
        Process process = builder.start();
        StreamGobbler streamGobbler =
                new StreamGobbler(process.getInputStream(), consumer); //System.out::println
        Executors.newSingleThreadExecutor().submit(streamGobbler);
        int exitCode = process.waitFor();
        finish.accept(exitCode);
        assert exitCode == 0;
    }

    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                    .forEach(consumer);
        }
    }

}




