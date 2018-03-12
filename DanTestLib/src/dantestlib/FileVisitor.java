/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dantestlib;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author dmcd2356
 */
public class FileVisitor extends SimpleFileVisitor<Path> {
  List<String> filepaths;
        
  public FileVisitor() {
    this.filepaths = new ArrayList<String>();
  }
        
  public List<String> getFilepaths() {
    return this.filepaths;
  }
        
  @Override
  public FileVisitResult visitFile(final Path aFile, final BasicFileAttributes aAttrs) throws IOException {
    this.filepaths.add(aFile.toString());
    return FileVisitResult.CONTINUE;
  }

}
