package com.cameo.commander;


import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.FluxSink;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
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
    private FluxProcessor<String, String> processor;
    private FluxSink<String> sink;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        log.info("Available commands: ");
        commands.forEach((key, val)-> log.info(" - {}: {}", key, val));
        this.processor = DirectProcessor.<String>create().serialize();
        this.sink = processor.sink();
    }

    @GetMapping(path = "/{command}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamFlux(@PathVariable String command) {
        String commandToExecute = commands.get(command);
        if (commandToExecute == null)
            processor.onNext("Command not found");
        else {
            executorService.submit(newExecuteTask(command, commandToExecute));
        }
        return processor.doOnNext(s -> log.info(s))
                .map(e -> ServerSentEvent.builder(e).build());
    }

    private Runnable newExecuteTask(String commandName, String commandToExecute) {
        return () -> {
            LocalDateTime localDateTime = lastExecution.get(commandName);
            if (localDateTime != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM-dd HH:mm");
                sink.next(commandName + ": previously executed " + formatter.format(localDateTime));
            }
            lastExecution.put(commandName, LocalDateTime.now());
            execute(commandToExecute,
                    sink::next,
                    finishCode -> {
                        sink.next(commandName + " FINISHED : " + finishCode);
                        lastExecution.put(commandName, LocalDateTime.now());
                        sink.complete();
                    });
        };
    }

    @SneakyThrows
    private void execute(String command, Consumer<String> consumer, Consumer<Integer> finish) {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");

        log.info("Executing command " + command);
        ProcessBuilder builder = new ProcessBuilder();
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("bash", "-c", command);
        }

        Process process = builder.start();

        Executors.newSingleThreadExecutor().submit(
                new StreamGobbler(process.getInputStream(), consumer));
        Executors.newSingleThreadExecutor().submit(
                new StreamGobbler(process.getErrorStream(),
                        s -> consumer.accept("ERROR " + s)));
        int exitCode = process.waitFor();
        finish.accept(exitCode);
        assert exitCode == 0;
    }

    private static class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final Consumer<String> consumer;

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




