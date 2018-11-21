/*
 * This file is a part of Mossy.
 * Copyright (c) 2018, Max Roncace <mproncace@gmail.com>
 *
 * The MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package net.caseif.mossy;

import net.caseif.mossy.assembly.ProgramAssembler;
import net.caseif.mossy.util.exception.AssemblerException;
import net.caseif.mossy.util.exception.InvalidAssemblyException;
import net.caseif.mossy.util.exception.LexerException;
import net.caseif.mossy.util.exception.ParserException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Nullable;

public class Main {

    private static final DirectoryStream.Filter<Path> ASM_FILTER = p -> p.getFileName().toString().endsWith(".asm");

    public static void main(String[] args) {
        Path inputPath = Paths.get(args[0]);
        Path outputPath = null;

        if (args.length == 2) {
            outputPath = Paths.get(args[1]);
        }

        try {
            assemble(inputPath, outputPath);
        } catch (IOException | InvalidAssemblyException ex) {
            ex.printStackTrace();
            System.err.println("Failed to assemble program.");
        }
    }

    private static void assemble(Path inputPath, @Nullable Path outputPath)
            throws IOException, InvalidAssemblyException  {
        if (!Files.exists(inputPath)) {
            throw new IOException("No such file " + inputPath.toString() + ".");
        }

        if (Files.isDirectory(inputPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputPath, ASM_FILTER)) {
                for (Path child : stream) {
                    assemble(child, null);
                }
            }
        } else {
            ProgramAssembler assembler = new ProgramAssembler();

            try (InputStream input = Files.newInputStream(inputPath)) {
                System.out.println("Starting assembly of file " + inputPath + ".");
                assembler.read(input);
            }

            if (outputPath == null) {
                String fileName = parseFileName(inputPath);
                outputPath = inputPath.getParent().resolve(fileName + ".bin");
            }

            System.out.println(outputPath);

            long start = System.nanoTime();
            assembler.assemble(Files.newOutputStream(outputPath));
            long end = System.nanoTime();
            System.out.println("Assembled " + inputPath.toString() + " in " + (end - start) + " ns");
        }
    }

    private static String parseFileName(Path inputPath) {
        if (!inputPath.getFileName().toString().contains(".")) {
            return inputPath.getFileName().toString();
        }

        String[] split = inputPath.getFileName().toString().split("\\.");
        StringBuilder fileNameB = new StringBuilder();
        for (int i = 0; i < split.length - 1; i++) {
            fileNameB.append(split[i]);
        }
        return fileNameB.toString();
    }

}
