/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dantestlib;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;

/**
 *
 * @author dmcd2356
 */
public class ZippedCleanupVisitor extends SimpleFileVisitor<Path>
{
    private Path root;
    
    public ZippedCleanupVisitor(final String root) {
        this.root = Paths.get(root, new String[0]);
    }
    
    @Override
    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attr) {
        final int i = file.toString().lastIndexOf(".");
        if (i > 0 && file.toString().substring(i).equalsIgnoreCase(".zip")) {
          this.deleteFile(file);
        }
        return FileVisitResult.CONTINUE;
    }
    
    private void deleteFile(final Path file) {
        try {
            Files.delete(file);
        }
        catch (NoSuchFileException x) {
            System.err.println(x);
        }
        catch (DirectoryNotEmptyException x2) {
            System.err.println(x2);
        }
        catch (IOException x3) {
            System.err.println(x3);
        }
    }
    
    @Override
    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) {
        if (dir.toFile().list().length == 0 && !dir.equals(this.root)) {
            this.postVisitDirectoryHelper(dir);
        }
        return FileVisitResult.CONTINUE;
    }
    
    @Override
    public FileVisitResult visitFileFailed(final Path file, final IOException exc) {
        System.err.println(exc);
        return FileVisitResult.CONTINUE;
    }
    
    private void postVisitDirectoryHelper(final Path dir) {
        this.deleteFile(dir);
    }
}