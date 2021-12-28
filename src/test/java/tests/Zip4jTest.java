
/*
 * de.unkrig.zip4jadapter - zip4j adapter to de.unkrig.commons.compress
 *
 * Copyright (c) 2021, Arno Unkrig
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import de.unkrig.commons.io.Readers;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.LocalFileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.EncryptionMethod;

/**
 * Tests Linagla's "Zip4j" library.
 */
public
class Zip4jTest {

    @Test public void
    testZipStreams() throws Exception {

        final String unencryptedText = "World";
        final String zipEntryName    = "dir/mystream";
        final char[] password        = "password".toCharArray();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, password)) {
            ZipParameters zipParameters = new ZipParameters();
            zipParameters.setFileNameInZip(zipEntryName);
            zipParameters.setEncryptFiles(true);                        // Default is false.
            zipParameters.setCompressionLevel(CompressionLevel.HIGHER); // Default is NORMAL.
            zipParameters.setEncryptionMethod(EncryptionMethod.AES);    // Default is NONE.
            zos.putNextEntry(zipParameters);
            Zip4jTest.write(unencryptedText, zos);
        }

        String decryptedText;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(baos.toByteArray()), password)) {
            LocalFileHeader e = zis.getNextEntry();
            Assert.assertEquals(zipEntryName, e.getFileName());
            decryptedText = Zip4jTest.read(zis);
            Assert.assertNull(zis.getNextEntry());
        }
        Assert.assertEquals(unencryptedText, decryptedText);
    }

    @Test public void
    testZipFile() throws Exception {

        final File   unencryptedFile      = new File("aFile.txt");
        final String unencryptedText      = "Hello";
        final File   archiveFile          = new File("compressed.zip");
        final char[] password             = "password".toCharArray();
        final File   destinationDirectory = new File("destination_directory");

        Zip4jTest.store(unencryptedText, unencryptedFile);

        try (ZipFile zipFile = new ZipFile(archiveFile, password)) {

            ZipParameters zipParameters = new ZipParameters();
            zipParameters.setEncryptFiles(true);                        // Default is false.
            zipParameters.setCompressionLevel(CompressionLevel.HIGHER); // Default is NORMAL.
            zipParameters.setEncryptionMethod(EncryptionMethod.AES);    // Default is NONE.

            zipFile.addFile(unencryptedFile, zipParameters);
        }

        destinationDirectory.mkdirs();
        try (ZipFile zipFile = new ZipFile(archiveFile, password)) {
            zipFile.extractFile("aFile.txt", destinationDirectory.getPath());
        }
        String decryptedText = Zip4jTest.load(new File(destinationDirectory, unencryptedFile.getName()));

        Assert.assertEquals(unencryptedText, decryptedText);
    }

    private static String
    load(File file) throws IOException {
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return Readers.readAll(r);
        }
    }

    private static void
    store(String text, File file) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(text);
        }
    }

    private static String
    read(InputStream is) throws IOException {
        return Readers.readAll(new InputStreamReader(is));
    }

    private static void
    write(String text, OutputStream os) throws IOException {
        Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        w.write(text);
        w.flush();
    }
}
