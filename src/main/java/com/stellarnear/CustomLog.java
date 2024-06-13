package com.stellarnear;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class CustomLog {
    private static Set<LogMsg> allLogs = new LinkedHashSet<>();
    private static SimpleDateFormat formater = new SimpleDateFormat("dd/MM/yy HH:mm:ss", Locale.FRANCE);

    private String currentLoggedClassName;
    private java.io.File logFile;
    private boolean debug=false;

    public CustomLog(Class<?> clazz) {
        this.currentLoggedClassName = clazz.getName();
        SimpleDateFormat logFormater = new SimpleDateFormat("yy_MM_dd HH_mm_ss", Locale.FRANCE);
        String pathLog = this.currentLoggedClassName + logFormater.format(new Date()) + ".log";
        logFile = new java.io.File(pathLog);
        try {
        logFile.createNewFile();
        } catch (IOException e) {
            System.out.println("Could not create the log file");
        }
    }

    public static Set<LogMsg> getAllLogs() {
        return allLogs;
    }

    private void processDisplay(LogMsg logMsg) {
        System.out.println(logMsg.getCodedString());
        try {
            Files.write(Paths.get(logFile.getPath()), (logMsg.getCodedString()+"\n").getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.out.println("Could not write in the log file");
        }
    }

    public void debug(String msg) {
        if(debug){
            LogMsg logMsg = new LogMsg(Level.DEBUG, msg);
            allLogs.add(logMsg);
            processDisplay(logMsg);
        } 
    }

    public void info(String msg) {
        LogMsg logMsg = new LogMsg(Level.INFO, msg);
        allLogs.add(logMsg);
        processDisplay(logMsg);
    }

    public void warn(String msg) {
        LogMsg logMsg = new LogMsg(Level.WARN, msg);
        allLogs.add(logMsg);
        processDisplay(logMsg);
    }

    public void warn(String msg, Exception e) {
        LogMsg logMsg = new LogMsg(Level.WARN, msg, e);
        allLogs.add(logMsg);
        processDisplay(logMsg);
    }

    public void err(String msg) {
        LogMsg logMsg = new LogMsg(Level.ERROR, msg);
        allLogs.add(logMsg);
        processDisplay(logMsg);
    }

    public void err(String msg, Exception e) {
        LogMsg logMsg = new LogMsg(Level.ERROR, msg, e);
        allLogs.add(logMsg);
        processDisplay(logMsg);
    }

    public void fatal(String msg, Exception e) {
        LogMsg logMsg = new LogMsg(Level.FATAL_ERROR, msg, e);
        allLogs.add(logMsg);
        processDisplay(logMsg);
    }

    public class LogMsg {
        private String prefix = currentLoggedClassName;
        private Level level;
        private String timeStamp;
        private String msg;
        private Exception exception;

        private LogMsg(Level level, String msg) {
            this.level = level;
            this.timeStamp = formater.format(new Date());
            this.msg = msg;
        }

        private LogMsg(Level level, String msg, Exception e) {
            this.level = level;
            this.timeStamp = formater.format(new Date());
            this.msg = msg;
            this.exception = e;
        }

        public String getCodedString() {
            String line = "";
            line += "" + prefix;
            line += " (" + timeStamp + ")";

            switch (level) {
                // TODO trouver couleur terminal
                case DEBUG:
                    line += " [" + level + "]";
                    break;
                case INFO:
                    line += " [" + level + "]";
                    break;
                case WARN:
                    line += " [" + level + "]";
                    break;
                case ERROR:
                    line += " [" + level + "]";
                    break;
                case FATAL_ERROR:
                    line += " [" + level + "]";
                    break;
            }
            line += " " + msg;

            if (exception != null) {
                line += "\nError stacktrace : " + exception.toString();
                for (StackTraceElement elem : exception.getStackTrace()) {
                    line += elem.toString();
                }

                if (exception.getCause() != null) {

                    line += "Caused by : " + exception.getCause().getMessage();
                    for (StackTraceElement elem : exception.getCause().getStackTrace()) {
                        line += elem.toString();
                    }

                }
            }
            return line;
        }
    }

    private enum Level {
        DEBUG, INFO, WARN, ERROR, FATAL_ERROR
    }

}