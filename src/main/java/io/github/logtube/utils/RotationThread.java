package io.github.logtube.utils;


import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class RotationThread extends Thread {

    public RotationThread() {
        this.dirs = new HashSet<>();
        this.signalFiles = new HashSet<>();
        this.project = "";
    }

    private enum Mode {
        None,
        Daily,
        Size
    }

    private Mode mode = Mode.None;

    private long size = 256 * 1024 * 1024;

    private int keep = 0;

    @NotNull
    private String project;

    @NotNull
    private Set<String> dirs;

    @NotNull
    private Set<String> signalFiles;

    public static void collectFiles(Set<String> result, File file) {
        if (file.isDirectory()) {
            for (File f : file.listFiles()) {
                collectFiles(result, f);
            }
        } else {
            String abs = file.getAbsolutePath();
            if (abs.toLowerCase().endsWith(".log")) {
                result.add(abs);
            }
        }
    }

    public void setup(@NotNull String mode, int keep, @NotNull Set<String> dirs, @NotNull Set<String> signalFiles, @NotNull String project) {
        synchronized (this) {
            if (mode.equalsIgnoreCase("daily")) {
                this.mode = Mode.Daily;
                this.size = 0;
            } else if (mode.toLowerCase().endsWith("mb")) {
                this.mode = Mode.Size;
                this.size = Long.parseLong(mode.substring(0, mode.length() - 2)) * 1024 * 1024;
            } else {
                this.mode = Mode.None;
                this.size = 0;
            }
            this.project = project;
            this.keep = keep;
            this.dirs = dirs;
            this.signalFiles = signalFiles;
        }
    }

    private boolean rotateDirs() {
        AtomicBoolean rotated = new AtomicBoolean(false);
        // 收集所有以 .log 结尾的文件名
        Set<String> files = new HashSet<>();
        this.dirs.forEach((d) -> collectFiles(files, new File(d)));
        // 汇总成日志文件组
        HashMap<String, RotationFile> rotationFiles = RotationFile.fromFiles(files, this.project);
        // 对每组日志文件进行处理
        rotationFiles.forEach((filename, rf) -> {
            // 列出所有标记
            Set<String> marks = rf.getMarks();
            // 列出不合规的标记
            Set<String> badMarks = marks.stream().filter((m) -> {
                if (this.mode == Mode.Daily) {
                    return !m.matches("^\\d{4}-\\d{2}-\\d{2}$");
                } else if (this.mode == Mode.Size) {
                    return !m.matches("^\\d+$");
                } else {
                    return false;
                }
            }).collect(Collectors.toSet());
            // 删除标记不合规的日志文件
            badMarks.forEach((m) -> {
                marks.remove(m);
                new File(RotationFile.deriveFilename(filename, m)).delete();
            });
            // 对所有 Mark 进行递增排序
            ArrayList<String> markList = new ArrayList<>(marks);
            markList.sort(Comparator.comparing(String::toString));
            // 第一步，删除超期文件
            if (this.keep > 0 && marks.size() > this.keep) {
                // 递增排序后，删除前 N-K 个文件
                List<String> deleteList = markList.subList(0, marks.size() - this.keep + 1);
                deleteList.forEach((m) -> new File(RotationFile.deriveFilename(filename, m)).delete());
            }
            // 第二步，判断然后轮转
            if (this.mode == Mode.Daily) {
                String newMark = Dates.formatDateMark(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
                // 昨日已经存在，退出，不进行轮转
                if (marks.contains(newMark)) {
                    return;
                }
                // 重命名文件
                String newFilename = RotationFile.deriveFilename(filename, newMark);
                new File(filename).renameTo(new File(newFilename));
                rotated.set(true);
            } else if (this.mode == Mode.Size) {
                // 文件还小，退出，不进行轮转
                if (new File(filename).length() < this.size) {
                    return;
                }
                // 确定下一个轮转名
                long nextId = 1;
                // 因为 markList 已经进行排序了，直接取最后一个文件就是最大的
                if (!markList.isEmpty()) {
                    try {
                        nextId = Long.parseLong(markList.get(markList.size() - 1)) + 1;
                    } catch (NumberFormatException ignored) {
                        return;
                    }
                }
                // 重命名文件
                String newFilename = RotationFile.deriveFilename(filename, String.format("%012d", nextId));
                new File(filename).renameTo(new File(newFilename));
                rotated.set(true);
            }
        });
        return rotated.get();
    }

    private void touchSignals() {
        // 更新所有信号文件的最后修改日期，触发日志文件重新打开
        this.signalFiles.forEach((f) -> {
            try {
                FileWriter fw = new FileWriter(f);
                fw.close();
            } catch (IOException ignored) {
            }
            new File(f).setLastModified(System.currentTimeMillis());
        });
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                Thread.sleep(1000 * 60);
            } catch (InterruptedException ignored) {
                return;
            }

            synchronized (this) {
                if (this.mode == Mode.None) {
                    continue;
                }
                try {
                    if (rotateDirs()) {
                        touchSignals();
                    }
                } catch (Exception e) {
                    // 日志系统出错，必然不能通过日志系统输出，只能靠 System.out 了
                    e.printStackTrace();
                }
            }
        }
    }

}
