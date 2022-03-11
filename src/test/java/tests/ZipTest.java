
/*
 * de.unkrig.zip4jadapter - zip4j adapter to de.unkrig.commons.compress
 *
 * Copyright (c) 2022, Arno Unkrig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import org.junit.Ignore;
import org.junit.Test;

import de.unkrig.commons.lang.protocol.RunnableWhichThrows;

public class ZipTest {

    @Ignore
    @Test @SuppressWarnings("unchecked") public void
    testDirEntries() throws Exception {

        // Create a .zip file, store exactly *one* directory entry in it; open the .zip file, read the entry from it, and
        // print the entry's "compression methode" and CRC to STDOUT.
        File   file         = new File ("./ZipTest_file.zip");
        String dirEntryName = "dir/dir/";

        // Create the .zip file in various ways.
        for (RunnableWhichThrows<Exception> zipFileCreator : (RunnableWhichThrows<Exception>[]) new RunnableWhichThrows[] {
            new RunnableWhichThrows<Exception>() {

                @Override public void
                run() throws Exception {
                    try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(file))) {
                        zos.putNextEntry(new java.util.zip.ZipEntry(dirEntryName));
                    }
                }

                @Override
                public String toString() { return  "java.util.zip.ZipOutputStream"; }
            },
            new RunnableWhichThrows<Exception>() {

                @Override public void
                run() throws Exception {
                    try (org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream zaos = new org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream(file)) {
                        zaos.putArchiveEntry(new org.apache.commons.compress.archivers.zip.ZipArchiveEntry(dirEntryName));
                        zaos.closeArchiveEntry();
                    }
                }

                @Override
                public String toString() { return  "org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream"; }
            },
            new RunnableWhichThrows<Exception>() {

                @Override public void
                run() throws Exception {

                    de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormat
                    af = de.unkrig.zip4jadapter.archivers.zip.ZipArchiveFormat.get();

                    try (org.apache.commons.compress.archivers.ArchiveOutputStream aos = af.create(file)) {
                        aos.putArchiveEntry(new org.apache.commons.compress.archivers.zip.ZipArchiveEntry(dirEntryName));
                        aos.closeArchiveEntry();
                    }
                }

                @Override
                public String toString() { return  "de.unkrig.zip4jadapter.archivers.zip.ZipArchiveFormat"; }
            },
            new RunnableWhichThrows<Exception>() {

                @Override public void
                run() throws Exception {

                    de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormat
                    af = de.unkrig.commons.file.org.apache.commons.compress.archivers.zip.ZipArchiveFormat.get();

                    try (org.apache.commons.compress.archivers.ArchiveOutputStream aos = af.create(file)) {
                        aos.putArchiveEntry(new org.apache.commons.compress.archivers.zip.ZipArchiveEntry(dirEntryName));
                        aos.closeArchiveEntry();
                    }
                }

                @Override
                public String toString() { return  "de.unkrig.commons.file.org.apache.commons.compress.archivers.zip.ZipArchiveFormat"; }
            },
        }) {

            // Read the .zip file in various ways.
            for (RunnableWhichThrows<Exception> zipFileReader : (RunnableWhichThrows<Exception >[]) new RunnableWhichThrows[] {
                new RunnableWhichThrows<Exception>() {

                    @Override public void
                    run() throws Exception {
                        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new FileInputStream(file))) {
                            java.util.zip.ZipEntry ze = zis.getNextEntry();
                            System.out.println(ze.getMethod() + ", " + ze.getCrc());
                        }
                    }

                    @Override
                    public String toString() { return  "java.util.zip.ZipInputStream"; }
                },
                new RunnableWhichThrows<Exception>() {

                    @Override public void
                    run() throws Exception {
                        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(file)) {
                            java.util.zip.ZipEntry ze = zf.entries().nextElement();
                            System.out.println(ze.getMethod() + ", " + ze.getCrc());
                        }
                    }

                    @Override
                    public String toString() { return  "java.util.zip.ZipFile"; }
                },
                new RunnableWhichThrows<Exception>() {

                    @Override public void
                    run() throws Exception {
                        try (net.lingala.zip4j.ZipFile zf = new net.lingala.zip4j.ZipFile(file)) {
                            net.lingala.zip4j.model.FileHeader fh = zf.getFileHeader(dirEntryName);
                            System.out.println(fh.getCompressionMethod().ordinal() + ", " + fh.getCrc());
                        }
                    }

                    @Override
                    public String toString() { return  "net.lingala.zip4j.ZipFile"; }
                },
            }) {

                System.out.printf("%-90s => %s%n", zipFileCreator, zipFileReader);
                zipFileCreator.run();
                zipFileReader.run();
            }
        }
    }
}
