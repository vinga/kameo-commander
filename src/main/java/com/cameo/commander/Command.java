package com.cameo.commander;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Data
public class Command {
    private String name;
    private String aliases;
    private String command;
    private String tags;
    private List<Parameter> parameters;

    @Data
    public static class Parameter {
        private String name;
        private String defaultValue;
        private String description;
    }

    public boolean isSameNameOrAlias(String name) {
        if (this.name.equals(name)) {
            return true;
        }
        if (tags != null) {
            return Arrays.asList(tags.split(",")).contains(name);
        }
        return false;
    }

    public String getCommandFormatted(Map<String, String> allRequestParams) {

        if (parameters != null) {
            Set<String> paramKeys = parameters.stream().map(s -> s.name).collect(Collectors.toSet());
            allRequestParams.keySet().forEach(key -> {
                if (!paramKeys.contains(key)) {
                    throw new IllegalArgumentException("Not supported parameter name " + key);
                }
            });
            parameters.forEach(pk -> {
                if (!allRequestParams.containsKey(pk.name)) {
                    allRequestParams.put(pk.name, pk.defaultValue);
                }
            });
        }
        if (parameters == null || parameters.isEmpty()) {
            return command;
        }
        AtomicReference<String> commandRef = new AtomicReference<>(command);


        allRequestParams.forEach((key, value) -> {
            String replaced = commandRef.get().replace("{" + key + "}", value);
            commandRef.set(replaced);
        });
        return commandRef.get();

    }

    private RuntimeInfo runtimeInfo = new RuntimeInfo();

    @Data
    class RuntimeInfo {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:dd")
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        @JsonSerialize(using = LocalDateTimeSerializer.class)
        LocalDateTime lastExecution;
        String log;

        public void addLog(String str) {
            if (log == null)
                log = "";
            log += str + "<br>";
        }
    }

}
