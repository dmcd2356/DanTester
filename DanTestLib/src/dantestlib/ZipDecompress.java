/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dantestlib;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *
 * @author dmcd2356
 */
public class ZipDecompress {
  private static final int FILE_LIMIT = 15;
  private static final long FILE_SIZE_LIMIT = 16777216L; //3145728L;
  private final Random random;
  private final LinkedList<Entry> queue;

  public ZipDecompress() {
    this.random = new Random();
    this.queue = new LinkedList<>();
  }
    
  public boolean decompress(String filePathName, String outPathName) {
    int fileCount = 0;
    Path filePath = Paths.get(filePathName);
    Path target = Paths.get(outPathName);
    File file = filePath.toFile();
    if (file.isDirectory()) {
      processDir(file, target);
    } else {
      this.processZipFile(filePath, target);
    }

    while (!this.queue.isEmpty() && fileCount < FILE_LIMIT) {
      Entry next = this.queue.removeFirst();
      file = next.path.toFile();
      if (file.isDirectory()) {
        processDir(file, next.target);
      } else {
        this.processZipFile(next.path, next.target);
      }
      ++fileCount;
    }
    if (!this.queue.isEmpty()) {
      ZippedCleanupVisitor visitor = new ZippedCleanupVisitor(outPathName);
      try {
        Files.walkFileTree(target, visitor);
      } catch (IOException ex) {
        System.out.println("Error in walkFileTree");
      }
      return false;
    }
    return true;
  }
    
  public void processDir(File file, Path target) {
    for (File f : file.listFiles()) {
      this.queue.add(new Entry(f.toPath(), target));
    }
  }

  public void processZipFile(Path file, Path target) {
    try (ZipInputStream zis = new ZipInputStream(new FileInputStream(file.toString()))) {
      int entryCount = 0;
      byte[] buf = new byte[1024];
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null && entryCount < FILE_LIMIT) {
        ++entryCount;
        String name = entry.getName();
        long len = entry.getSize();
        if (!entry.isDirectory()) {
          String pathname = Paths.get(name).getFileName().toString();
          String randname = String.valueOf(random.nextLong());
          if (randname.startsWith("-")) {
            randname = randname.substring(1);
          }
          Path loc = target.resolve(randname).resolve(pathname);
          File newFile = loc.toFile();
          int count = 0;
          long totalBytesWritten = 0L;
          newFile.getParentFile().mkdirs();
          try (FileOutputStream collector = new FileOutputStream(loc.toString())) {
            do {
              count = zis.read(buf, 0, buf.length);
              if (count > 0) {
                collector.write(buf, 0, count);
              }
              totalBytesWritten += count;
            } while (count > 0 && totalBytesWritten < FILE_SIZE_LIMIT);
            if (totalBytesWritten >= FILE_SIZE_LIMIT) {
              throw new RuntimeException("File is too large: " + name);
            }
            this.queue.add(new Entry(loc, target));
//            System.out.println(name + ": " + totalBytesWritten);
            System.out.println(loc.toString() + ": " + totalBytesWritten);
            collector.close();
          }
        }
      }

      // do cleanup
//      long size = 0L;
//      if (file.startsWith(target)) {
//        File theFile = file.toFile();
//        size = theFile.length();
//        Files.delete(file);
//        Path parent = file.getParent();
//        Files.delete(parent);
//      }
    } catch (IOException e) {
      System.err.println("Error processing file");
    }
  }
    
  class Entry {
    Path path;
    Path target;
        
    Entry(Path p, Path t) {
      this.path = p;
      this.target = t;
    }
  }
}