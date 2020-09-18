package com.cameo.commander;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import reactor.core.publisher.DirectProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
@RestController
@ConfigurationProperties(prefix = "commander")
public class HomeController {

    @Setter
    private List<Command> commands;
    @Setter
    private List<Tag> tags;


    // private final Map<String, LocalDateTime> lastExecution = new HashMap<>();
    private FluxProcessor<String, String> processor;
    private FluxSink<String> sink;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();


    private Command findCommandByName(String name) {
        log.info("Searching command for name " + name);
        return commands.stream().filter(f -> f.isSameNameOrAlias(name)).findAny().orElseThrow(RuntimeException::new);
    }

    @PostConstruct
    public void init() {
        log.info("Available commands: ");
        commands.forEach((cmd) -> log.info("{} - {}: {}, {}", cmd.getName(), cmd.getName(), cmd.getAliases(), cmd.getTags()));
        this.processor = DirectProcessor.<String>create().serialize();
        this.sink = processor.sink();
    }

    @GetMapping(path = "/tags-list/{tag}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Command> showTag(@PathVariable String tag) {
        Optional<Tag> otag = tags.stream().filter(f -> f.getName().equals(tag)).filter(f -> f.isPublishList())
                .findAny();
        if (!otag.isPresent()) {
            return Collections.emptyList();
        }
        return commands.stream()
                .filter(f -> f.getTags() != null && Arrays.asList(f.getTags().split(",")).contains(tag))
                .collect(Collectors.toList());
    }
    String getRuntimeInfo(Command cmd) {
        if (cmd.getRuntimeInfo()==null)
            return "";
        else {
            return "<div>lastExecution: "+cmd.getRuntimeInfo().getLastExecution()+"</div>";
        }
    }
    String getIfNotEmpty(String name, String val) {
        if (StringUtils.isEmpty(val)) {
            return "";
        } else {
            return "<div>"+name+": "+val+"</div>";
        }
    }

    String showLog(Command cmd) {
        if (StringUtils.isEmpty(cmd.getRuntimeInfo().getLog())) {
            return "";
        } else {
            return "<div style='cursor: pointer; color: #00E4FF' onClick='document.getElementById(\"idlogs\").innerHTML=\""+
                    cmd.getRuntimeInfo().getLog()
                            .replace("\"", "[dq]")
                            .replace("'", "[sq]")
                            .replace("\n", "<br/>")
                            .replace("\r", "")
                    +"\"'>show log</div>";
        }
    }

    @Autowired
    private ObjectMapper objectMapper;
    @SneakyThrows
    @GetMapping(path = "/tags/{tag}.html", produces = MediaType.TEXT_HTML_VALUE)
    public String showTagHtml(@PathVariable String tag) {
        Optional<Tag> otag = tags.stream().filter(f -> f.getName().equals(tag)).filter(f -> f.isPublishList())
                .findAny();
        if (!otag.isPresent()) {
            return "<html>n/a</html>";
        }

        if (true) {
            List<Command> commands = this.commands.stream()
                    .filter(f -> f.getTags() != null && Arrays.asList(f.getTags().split(",")).contains(tag))
                    .collect(Collectors.toList());




            Resource resource = new ClassPathResource("tag.html");
            try (Reader reader = new InputStreamReader(resource.getInputStream(), UTF_8)) {
                String res= FileCopyUtils.copyToString(reader);
                res=res.replaceAll("\\$\\{tagGroupName}", Matcher.quoteReplacement(tag));
                res=res.replaceAll("\\$\\{commands}", Matcher.quoteReplacement(objectMapper.writeValueAsString(commands)));

                return res;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return "<html style='margin:30px'><div>"+commands.stream()
                .filter(f -> f.getTags() != null && Arrays.asList(f.getTags().split(",")).contains(tag))
                .map(f-> "<div style='display: flex'><a style='color: #00E4FF; width: 300px' href=\"/commands/"+f.getName()+"\">"+f.getName()+"</a>"
                        +"<div>command: "+f.getCommand()+"<br/>"
                        +getIfNotEmpty("tags", f.getTags())
                        +getIfNotEmpty("aliases", f.getAliases())
                        +getRuntimeInfo(f)
                        +showLog(f)

                        +"</div></div>"+"<br/>")
                .collect(Collectors.joining())+"</div><br/><br/><div id='idlogs'></div></html>";
    }

/*    @GetMapping(path = "/commands/processor/{command}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamFluxOld(@PathVariable String command) {
        Command cmd = findCommandByName(command);
        String commandToExecute = cmd.getCommand();
        if (commandToExecute == null)
            sink.next("Command not found");
        else {
            sink.next("Command started");
            executorService.submit(newExecuteTask(cmd, command, commandToExecute, sink::next, sink::complete));
        }
        return processor.doOnNext(s -> log.info(s))
                .map(e -> ServerSentEvent.builder(e).build());
    }

    @GetMapping(path = "/commands/push/{command}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamFlux(@PathVariable String command) {
        return Flux.<String>push(emitter -> {
            Command cmd = findCommandByName(command);
            String commandToExecute = cmd.getCommand();
            if (commandToExecute == null)
                sink.next("Command not found");
            else {
                sink.next("Command started");
                executorService.submit(newExecuteTask(cmd, command, commandToExecute, emitter::next, emitter::complete));
            }

        }).map(e -> ServerSentEvent.builder(e).build());
    }*/


    //@GetMapping(path = "/commands/**", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    //public Flux<ServerSentEvent<String>> streamFluxWithCreate(ServletWebRequest request) {
    //    int i = request.uri().indexOf("/commands/");
      //  String command=request.uri().substring(i+"/commands/".length());

    @GetMapping(path = "/commands/{c1}/{c2}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamFluxWithCreate(@PathVariable String c1, @PathVariable String c2, @RequestParam Map<String,String> allRequestParams) {
        return streamFluxWithCreate(c1+"/"+c2, allRequestParams);
    }

    @GetMapping(path = "/commands/{command}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamFluxWithCreate(@PathVariable String command, @RequestParam Map<String,String> allRequestParams) {
        return Flux.<String>create(fluxSink -> {
            Command cmd = findCommandByName(command);
            String commandToExecute = cmd.getCommandFormatted(allRequestParams);
            log.info("formatted command "+commandToExecute);


            if (commandToExecute == null)
                sink.next("Command not found");
            else {
                sink.next("Command started");
                executorService.submit(newExecuteTask(cmd, command, commandToExecute, fluxSink::next, fluxSink::complete));
            }

        }).map(e -> ServerSentEvent.builder(e).build());
    }


    private Runnable newExecuteTask(Command cmd, String commandName, String commandToExecute, Consumer<String> nextConsumer, Runnable onComplete) {
        return () -> {
            LocalDateTime localDateTime = cmd.getRuntimeInfo().getLastExecution();
            if (localDateTime != null) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM-dd HH:mm");
                nextConsumer.accept(commandName + ": previously executed " + formatter.format(localDateTime));
            }
            cmd.getRuntimeInfo().setLastExecution(LocalDateTime.now());
            cmd.getRuntimeInfo().setLog("");
            execute(commandName, commandToExecute,
                    nextConsumer.andThen(str->cmd.getRuntimeInfo().addLog(str)),
                    finishCode -> {
                        nextConsumer.accept(commandName + " FINISHED : " + finishCode);
                        cmd.getRuntimeInfo().setLastExecution(LocalDateTime.now());
                        onComplete.run();
                    });
        };
    }

    @SneakyThrows
    private void execute(String commandName, String command, Consumer<String> consumer, Consumer<Integer> finish) {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");

        log.info("{} Executing command {}", commandName, command);
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
        log.info("{} Waiting for command finish", commandName);
        int exitCode = process.waitFor();
        finish.accept(exitCode);
        log.info("{} Finished with code {}", commandName, exitCode);
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




