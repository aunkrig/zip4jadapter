
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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Date;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.utils.Charsets;
import org.junit.Assert;
import org.junit.Test;

import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormat;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.io.Readers;
import de.unkrig.commons.lang.protocol.ConsumerWhichThrows;
import de.unkrig.commons.nullanalysis.Nullable;
import de.unkrig.zip4jadapter.archivers.zip.ZipArchiveFormat;
import net.lingala.zip4j.exception.ZipException;

public class Zip4jAdapterTest {

    private static final ArchiveFormat af;
    static {
        try {
            af = ArchiveFormatFactory.forFormatName("zip");
        } catch (ArchiveException ae) {
            throw new AssertionError(ae);
        }
        Assert.assertEquals("de.unkrig.zip4jadapter.archivers.zip.ZipArchiveFormat", Zip4jAdapterTest.af.getClass().getName());
    }

    /**
     * Tests the ZIP {@link ArchiveInputStream} and {@link ArchiveOutputStream}.
     */
    @Test public void
    testZipArchiveStream() throws Exception {

        String entryName        = "file";
        String text             = "foobar";
        Date   lastModifiedDate = new Date(1_000_000_000_000L); // 2001-09-09
        String password         = "password";

        // Unencrypted.
        System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPT, "false");
        System.clearProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_FILE_PASSWORD);
        System.clearProperty(ZipArchiveFormat.SYSTEM_PROPERTY_INPUT_FILE_PASSWORD);
        Zip4jAdapterTest.verifyArchiveStream(entryName, text, lastModifiedDate);

        // Various encryption methods.
        for (String em : new String [] { "zip_standard", "aES" }) {
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPT, "true");
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPTION_METHOD, em);
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_FILE_PASSWORD, password);
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_INPUT_FILE_PASSWORD, password);
            Zip4jAdapterTest.verifyArchiveStream(entryName, text, lastModifiedDate);
        }

        // Read encrypted stream with wrong or no password.
        for (String inputPassword : new String [] { password + "x", null }) {
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPT, "true");
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPTION_METHOD, "zip_standard");
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_FILE_PASSWORD, password);
            Zip4jAdapterTest.setOrClearSystemProperty(ZipArchiveFormat.SYSTEM_PROPERTY_INPUT_FILE_PASSWORD, inputPassword);
            try {
                Zip4jAdapterTest.verifyArchiveStream(entryName, text, lastModifiedDate);
                Assert.fail();
            } catch (ZipException ze) {
                Assert.assertTrue(ze.getMessage().contains("assword"));
            }
        }
    }

    private static void
    verifyArchiveStream(String entryName, String text, Date lastModifiedDate) throws Exception {
        Zip4jAdapterTest.verifyArchiveStream(
            Zip4jAdapterTest.storeArchiveStream(entryName, lastModifiedDate, text),
            entryName,
            lastModifiedDate,
            text
        );
    }

    private static void
    setOrClearSystemProperty(String systemPropertyName, @Nullable String value) {
        if (value == null) {
            System.clearProperty(systemPropertyName);
        } else {
            System.setProperty(systemPropertyName, value);
        }
    }

    private static byte[]
    storeArchiveStream(String entryName, Date lastModifiedDate, String text) throws Exception {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ArchiveOutputStream aos = Zip4jAdapterTest.af.archiveOutputStream(baos)) {

            Zip4jAdapterTest.af.writeEntry(aos, entryName, lastModifiedDate, new ConsumerWhichThrows<OutputStream, IOException>() {
                @Override public void consume(OutputStream os) throws IOException { Zip4jAdapterTest.write(text, os); }
            });
        }

        return baos.toByteArray();
    }

    private static void
    verifyArchiveStream(byte[] data, String entryName, Date lastModifiedDate, String text) throws Exception {
        try (ArchiveInputStream ais = Zip4jAdapterTest.af.archiveInputStream(new ByteArrayInputStream(data))) {
            ArchiveEntry ae = ais.getNextEntry();
            Assert.assertEquals(entryName, ae.getName());
            Assert.assertEquals(ArchiveEntry.SIZE_UNKNOWN, ae.getSize());
            Assert.assertEquals(lastModifiedDate, ae.getLastModifiedDate());
            Assert.assertEquals(text, Zip4jAdapterTest.read(ais));

            Assert.assertNull(ais.getNextEntry());
        }
    }

    /**
     * Tests the {@code ZipArchiveFormat} implemented in the Zip4jAdapter.
     */
    @Test public void
    testZipArchiveFile() throws Exception {

        File   archiveFile      = new File("file.zip");
        String entryName        = "file";
        String text             = "foobar";
        Date   lastModifiedDate = new Date(1_000_000_000_000L); // 2001-09-09
        String password         = "password";

        // Unencrypted.
        System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPT, "false");
        System.clearProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_FILE_PASSWORD);
        System.clearProperty(ZipArchiveFormat.SYSTEM_PROPERTY_INPUT_FILE_PASSWORD);
        Zip4jAdapterTest.storeArchiveFile(archiveFile, entryName, text, lastModifiedDate);
        Zip4jAdapterTest.verifyArchiveFile(archiveFile, entryName, text, lastModifiedDate);

        // Various encryption methods.
        for (String em : new String [] { "zip_standard", "aES" }) {
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPT, "true");
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPTION_METHOD, em);
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_FILE_PASSWORD, password);
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_INPUT_FILE_PASSWORD, password);
            Zip4jAdapterTest.storeArchiveFile(archiveFile, entryName, text, lastModifiedDate);
            Zip4jAdapterTest.verifyArchiveFile(archiveFile, entryName, text, lastModifiedDate);
        }

        // Read encrypted stream with wrong or no password.
        for (String inputPassword : new String [] { password + "x", null }) {
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPT, "true");
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPTION_METHOD, "zip_standard");
            System.setProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_FILE_PASSWORD, password);
            Zip4jAdapterTest.setOrClearSystemProperty(ZipArchiveFormat.SYSTEM_PROPERTY_INPUT_FILE_PASSWORD, inputPassword);
            Zip4jAdapterTest.storeArchiveFile(archiveFile, entryName, text, lastModifiedDate);
            try {
                Zip4jAdapterTest.verifyArchiveFile(archiveFile, entryName, text, lastModifiedDate);
                Assert.fail();
            } catch (ZipException ze) {
                Assert.assertTrue(ze.getMessage().contains("assword"));
            }
        }

    }

    private static void
    storeArchiveFile(File archiveFile, String entryName, String text, Date lastModifiedDate) throws Exception {
        try (ArchiveOutputStream aos = Zip4jAdapterTest.af.create(archiveFile)) {
            Zip4jAdapterTest.af.writeEntry(aos, entryName, lastModifiedDate, os -> Zip4jAdapterTest.write(text, os));
        }
    }

    private static void
    verifyArchiveFile(File archiveFile, String entryName, String text, Date lastModifiedDate) throws Exception {
        try (ArchiveInputStream ais = Zip4jAdapterTest.af.open(archiveFile)) {
            ArchiveEntry ae = ais.getNextEntry();
            Assert.assertEquals(entryName, ae.getName());
            Assert.assertEquals(text.length(), ae.getSize());
            Assert.assertEquals(lastModifiedDate, ae.getLastModifiedDate());
            Assert.assertEquals(text, Zip4jAdapterTest.read(ais));

            Assert.assertNull(ais.getNextEntry());
        }
    }

    private static String
    read(InputStream is) throws IOException {
        return Readers.readAll(new InputStreamReader(is));
    }

    private static void
    write(String text, OutputStream os) throws IOException {
        Writer w = new OutputStreamWriter(os, Charsets.UTF_8);
        w.write(text);
        w.flush();
    }
}
