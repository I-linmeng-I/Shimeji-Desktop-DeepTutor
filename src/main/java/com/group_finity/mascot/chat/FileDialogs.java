package com.group_finity.mascot.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JFileChooser;
import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shows the file and folder pickers of the operating system instead of Swing's own chooser.
 * <p>
 * Swing's {@link JFileChooser} reproduces an interface that Windows dropped years ago. The modern Windows pickers
 * are only reachable through the shell's common item dialog, which .NET exposes through WinForms. This class
 * therefore runs a short PowerShell script that shows that dialog and prints what the user picked. If no shell
 * with the modern dialog can be started, it falls back to {@link JFileChooser} so the feature never dead-ends.
 */
public final class FileDialogs {
    private static final Logger log = LoggerFactory.getLogger(FileDialogs.class);

    /**
     * The output prefix of a picked file.
     */
    private static final String FILE_PREFIX = "FILE\t";

    /**
     * The output prefix of a picked folder.
     */
    private static final String DIR_PREFIX = "DIR\t";

    /**
     * The shells to try, most modern first. PowerShell 7 runs on .NET 5 or later, whose WinForms dialogs are the
     * same ones Explorer uses; Windows PowerShell 5.1 is kept as a fallback for machines without PowerShell 7.
     */
    private static final String[] SHELLS = {"pwsh", "powershell"};

    private FileDialogs() {
    }

    /**
     * Shows the system's open file dialog.
     *
     * @param parent the component the dialog belongs to
     * @param title the title of the dialog
     * @return the files the user picked, or an empty list if the dialog was cancelled
     */
    public static List<File> chooseFiles(final Component parent, final String title) {
        String script = wrap("""
                $d = New-Object System.Windows.Forms.OpenFileDialog
                $d.Title = %s
                $d.Multiselect = $true
                $d.CheckFileExists = $true
                $d.RestoreDirectory = $true
                $r = $d.ShowDialog($owner)
                if ($r -eq [System.Windows.Forms.DialogResult]::OK) {
                    foreach ($f in $d.FileNames) { Write-Output ("%s" + $f) }
                }
                """.formatted(powerShellString(title), FILE_PREFIX));

        List<String> lines = run(script);
        if (lines == null) {
            return chooseFilesWithSwing(parent, title);
        }

        List<File> result = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith(FILE_PREFIX)) {
                result.add(new File(line.substring(FILE_PREFIX.length())));
            }
        }
        return result;
    }

    /**
     * Shows the system's folder picker.
     *
     * @param parent the component the dialog belongs to
     * @param title the title of the dialog
     * @param initial the folder to start in, or {@code null} to let the shell decide
     * @return the folder the user picked, or {@code null} if the dialog was cancelled
     */
    public static Path chooseDirectory(final Component parent, final String title, final Path initial) {
        String initialPath = initial != null && Files.isDirectory(initial)
                ? "$d.SelectedPath = " + powerShellString(initial.toString()) + "\n" : "";
        String script = wrap("""
                $d = New-Object System.Windows.Forms.FolderBrowserDialog
                $d.Description = %s
                $d.ShowNewFolderButton = $true
                %s$r = $d.ShowDialog($owner)
                if ($r -eq [System.Windows.Forms.DialogResult]::OK) { Write-Output ("%s" + $d.SelectedPath) }
                """.formatted(powerShellString(title), initialPath, DIR_PREFIX));

        List<String> lines = run(script);
        if (lines == null) {
            return chooseDirectoryWithSwing(parent, title, initial);
        }

        for (String line : lines) {
            if (line.startsWith(DIR_PREFIX)) {
                return Path.of(line.substring(DIR_PREFIX.length()));
            }
        }
        return null;
    }

    /**
     * Wraps the body of a picker script in the parts that are the same for files and folders.
     * <p>
     * The dialog is given an invisible top-most owner window, otherwise it can open behind the always-on-top
     * chat panel and look as if the button did nothing.
     */
    private static String wrap(final String body) {
        return """
                $ErrorActionPreference = 'Stop'
                [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
                Add-Type -AssemblyName System.Windows.Forms
                Add-Type -AssemblyName System.Drawing
                $owner = New-Object System.Windows.Forms.Form
                $owner.FormBorderStyle = 'None'
                $owner.ShowInTaskbar = $false
                $owner.StartPosition = 'CenterScreen'
                $owner.Opacity = 0
                $owner.Size = New-Object System.Drawing.Size(1, 1)
                $owner.TopMost = $true
                $owner.Show()
                try {
                %s
                } finally {
                    $owner.Close()
                }
                """.formatted(indent(body));
    }

    private static String indent(final String body) {
        StringBuilder result = new StringBuilder();
        for (String line : body.stripIndent().split("\n", -1)) {
            if (!line.isBlank()) {
                result.append("    ").append(line);
            }
            result.append('\n');
        }
        return result.toString();
    }

    /**
     * Quotes a string as a PowerShell single-quoted literal.
     */
    private static String powerShellString(final String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    /**
     * Runs the script with the first shell that starts successfully.
     *
     * @return the output lines, or {@code null} if no shell could be started or the script failed
     */
    private static List<String> run(final String script) {
        Path scriptFile;
        try {
            scriptFile = Files.createTempFile("shimeji-dialog-", ".ps1");
            Files.writeString(scriptFile, script, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to prepare the system file dialog script", e);
            return null;
        }

        try {
            for (String shell : SHELLS) {
                List<String> lines = runWithShell(shell, scriptFile);
                if (lines != null) {
                    return lines;
                }
            }
            return null;
        } finally {
            try {
                Files.deleteIfExists(scriptFile);
            } catch (IOException ignored) {
                // A leftover temporary file does no harm
            }
        }
    }

    private static List<String> runWithShell(final String shell, final Path scriptFile) {
        ProcessBuilder builder = new ProcessBuilder(shell, "-NoProfile", "-NonInteractive", "-STA",
                "-ExecutionPolicy", "Bypass", "-File", scriptFile.toString());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.warn("The \"{}\" file dialog script exited with code {}", shell, exitCode);
                return null;
            }
            return lines;
        } catch (IOException e) {
            log.debug("The \"{}\" shell is not available", shell, e);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static List<File> chooseFilesWithSwing(final Component parent, final String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setMultiSelectionEnabled(true);
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return List.of();
        }
        return List.of(chooser.getSelectedFiles());
    }

    private static Path chooseDirectoryWithSwing(final Component parent, final String title, final Path initial) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (initial != null) {
            chooser.setCurrentDirectory(initial.toFile());
        }
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        return chooser.getSelectedFile().toPath();
    }
}
