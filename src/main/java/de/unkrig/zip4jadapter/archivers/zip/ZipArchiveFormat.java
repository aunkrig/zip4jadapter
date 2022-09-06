
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

package de.unkrig.zip4jadapter.archivers.zip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.compressors.FileNameUtil;

import de.unkrig.commons.file.org.apache.commons.compress.archivers.AbstractArchiveFormat;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormat;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveOutputStream2;
import de.unkrig.commons.io.OutputStreams;
import de.unkrig.commons.file.org.apache.commons.compress.archivers.ArchiveFormatFactory;
import de.unkrig.commons.lang.AssertionUtil;
import de.unkrig.commons.lang.protocol.ConsumerWhichThrows;
import de.unkrig.commons.nullanalysis.NotNullByDefault;
import de.unkrig.commons.nullanalysis.Nullable;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.io.inputstream.ZipInputStream;
import net.lingala.zip4j.io.outputstream.ZipOutputStream;
import net.lingala.zip4j.model.AbstractFileHeader;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.EncryptionMethod;

/**
 * Representation of the 'zip' archive format. This implementation supports encryption, which is controlled by a set of
 * system properties.
 *
 * @see #SYSTEM_PROPERTY_INPUT_FILE_PASSWORD
 * @see #SYSTEM_PROPERTY_OUTPUT_ENTRY_COMPRESSION_LEVEL
 * @see #SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPT
 * @see #SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPTION_METHOD
 * @see #SYSTEM_PROPERTY_OUTPUT_FILE_PASSWORD
 */
public final
class ZipArchiveFormat extends AbstractArchiveFormat {

    static { AssertionUtil.enableAssertionsForThisClass(); }

    /**
     * Iff a system property with this name is set, then its value is used to configure the compression level of all output zip entries created afterwards. That value
     * can be overridden with {@link #setOutputEntryCompressionLevel(CompressionLevel)}.
     */
    public static final String SYSTEM_PROPERTY_OUTPUT_ENTRY_COMPRESSION_LEVEL = "zip4j.outputEntryCompressionLevel";

    /**
     * Iff a system property with this name is set, then its value is used to decrypt zip input files. That password
     * can be overridden with {@link #setInputFilePasswordChars(byte[])}.
     */
    public static final String SYSTEM_PROPERTY_INPUT_FILE_PASSWORD            = "zip4j.inputFilePassword";

    /**
     * Iff a system property with this name is set, then its value is used to encrypt zip output files. That password
     * can be overridden with {@link #setOutputFilePasswordChars(byte[])}.
     */
    public static final String SYSTEM_PROPERTY_OUTPUT_FILE_PASSWORD           = "zip4j.outputFilePassword";

    /**
     * Iff a system property with this name is set, then its value is used to enable output file encryption. The
     * system property  can be overridden with {@link #setOutputEntryEncrypt(boolean)}.
     */
    public static final String SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPT           = "zip4j.outputEntryEncrypt";

    /**
     * Iff a system property with this name is set, then its value is used to encrypt zip output file entries. That
     * encryption method can be overridden with {@link #setOutputEntryEncryptionMethod(EncryptionMethod)}.
     */
    public static final String SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPTION_METHOD = "zip4j.outputEntryEncryptionMethod";

    private static final FileNameUtil FILE_NAME_UTIL = new FileNameUtil(Collections.singletonMap(".zip", ""), ".zip");

    @Nullable private static CompressionLevel outputEntryCompressionLevel;
    @Nullable private static char[]           inputPasswordChars;
    @Nullable private static char[]           outputPasswordChars;
    private static           boolean          outputEntryEncrypt;
    @Nullable private static EncryptionMethod outputEntryEncryptionMethod;

    private ZipArchiveFormat() {}

    /** Required by {@link ArchiveFormatFactory}. */
    public static ArchiveFormat
    get() { return ZipArchiveFormat.INSTANCE; }
    private static final ArchiveFormat INSTANCE = new ZipArchiveFormat();

    @Override public String
    getName() { return ArchiveStreamFactory.ZIP; }

    @Override public boolean
    isArchiveFileName(String fileName) { return ZipArchiveFormat.FILE_NAME_UTIL.isCompressedFilename(fileName); }

    @Override public String
    getArchiveFileName(String fileName) { return ZipArchiveFormat.FILE_NAME_UTIL.getCompressedFilename(fileName); }

    /**
     * Uses the password specified in the system property {@value ZipArchiveFormat#SYSTEM_PROPERTY_INPUT_FILE_PASSWORD}
     * to decrypt any encrypted archive entries of the <var>inputStream</var>. That password can be overridden with {@link #setInputFilePasswordChars(char[])}.
     */
    @Override public ArchiveInputStream
    archiveInputStream(InputStream is) {

        char[] ipcs = ZipArchiveFormat.inputPasswordChars;
        if (ipcs == null) ipcs = ZipArchiveFormat.toCharArray(System.getProperty(ZipArchiveFormat.SYSTEM_PROPERTY_INPUT_FILE_PASSWORD));

        return this.archiveInputStream(is, ipcs);
    }

    /**
     * Uses the given <var>password</var> to decrypt any encrypted archive entries of the <var>inputStream</var>.
     */
    public ArchiveInputStream
    archiveInputStream(InputStream is, @Nullable char[] password) {

        final ZipInputStream zis = new ZipInputStream(is, password);

        return new ArchiveInputStream() {

            @Override @NotNullByDefault(false) public ArchiveEntry
            getNextEntry() throws IOException {

                AbstractFileHeader afh;
                try {
                    afh = zis.getNextEntry();
                } catch (ZipException ze) {

                    // Fix up zip4j's misleading exception message for "missing password".
                    if (ze.getMessage().toLowerCase().contains("wrong password") && password == null) {
                        throw new ZipException("Password required", ze);
                    }
                    throw ze;
                }
                if (afh == null) return null;

                return ZipArchiveFormat.zipArchiveEntry(afh);
            }

            @Override @NotNullByDefault(false) public int
            read(byte[] b, int off, int len) throws IOException { return zis.read(b, off, len); }

            @Override
            public void close() throws IOException {
                zis.close();
                super.close();
            }
        };
    }

    /**
     * Uses the password specified in the system property {@value ZipArchiveFormat#SYSTEM_PROPERTY_INPUT_FILE_PASSWORD}
     * to decrypt any encrypted entries of the <var>archiveFile</var>.
     */
    @Override public ArchiveInputStream
    open(File archiveFile) throws IOException {

        char[] password = ZipArchiveFormat.inputPasswordChars;
        if (password == null) {
            password = ZipArchiveFormat.toCharArray(System.getProperty(ZipArchiveFormat.SYSTEM_PROPERTY_INPUT_FILE_PASSWORD));
        }

        return this.open(archiveFile, password);
    }

    /**
     * Uses the given <var>password</var> to decrypt any encrypted archive entries of the <var>archiveFile</var>.
     */
    public ArchiveInputStream
    open(File archiveFile, @Nullable char[] password) throws IOException {

        return new ZipArchiveInputStream() {

            final ZipFile                 zipFile             = new ZipFile(archiveFile, password);
            final List<FileHeader>        fileHeaders         = this.zipFile.getFileHeaders();
            Iterator<FileHeader>          fileHeadersIterator = this.fileHeaders.iterator();
            @Nullable private InputStream stream;

            @Override public int
            getCount() { return this.fileHeaders.size(); }

            @Override public long
            getBytesRead() { throw new UnsupportedOperationException("getBytesRead"); }

            @Override public int
            read(@Nullable byte[] b, int off, int len) throws IOException {
                InputStream is = this.stream;
                if (is == null) throw new IllegalStateException();
                return is.read(b, off, len);
            }

            @Override public void
            close() throws IOException { this.zipFile.close(); }

            @Override @Nullable public ArchiveEntry
            getNextEntry() throws IOException {

                if (!this.fileHeadersIterator.hasNext()) {
                    this.stream = null;
                    return null;
                }

                FileHeader fh = this.fileHeadersIterator.next();
                try {
                    this.stream = this.zipFile.getInputStream(fh);
                } catch (ZipException ze) {

                    // Fix up zip4j's misleading exception message for "missing password".
                    if (ze.getMessage().toLowerCase().contains("wrong password") && password == null) {
                        throw new ZipException("Password required", ze);
                    }
                    throw ze;
                }

                return ZipArchiveFormat.zipArchiveEntry(fh);
            }

            @Override public String
            toString() { return archiveFile.toString(); }
        };
    }

    /**
     * Uses the password specified in the system property {@value ZipArchiveFormat#SYSTEM_PROPERTY_OUTPUT_FILE_PASSWORD}
     * to encrypt all archive entries that will be created throught the returned {@link ArchiveOutputStream}.
     */
    @Override public ArchiveOutputStream
    archiveOutputStream(OutputStream os) throws ArchiveException {

        char[] opcs = ZipArchiveFormat.outputPasswordChars;
        if (opcs == null) opcs = ZipArchiveFormat.toCharArray(System.getProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_FILE_PASSWORD));

        return this.archiveOutputStream(os, opcs);
    }

    /**
     * Uses the given <var>password</var> to encrypt all archive entries that will be created throught the returned
     * {@link ArchiveOutputStream}.
     */
    public ArchiveOutputStream
    archiveOutputStream(OutputStream os, @Nullable char[] password) throws ArchiveException {

        try {
            return ZipArchiveFormat.zipArchiveOutputStream(os, password);
        } catch (IOException ioe) {
            throw new ArchiveException(null, ioe);
        }
    }

    private static ZipArchiveOutputStream
    zipArchiveOutputStream(OutputStream os, @Nullable char[] password) throws IOException {

        // Because the lingala ZipOutputStream has no "finish()" method (only "close()"), we must make the underlying
        // output stream unclosable.
        final ZipOutputStream zos = new ZipOutputStream(OutputStreams.unclosable(os), password);

        return new ZipArchiveOutputStream() {

            @Override @NotNullByDefault(false) public void
            putArchiveEntry(ArchiveEntry entry) throws IOException {

                CompressionLevel compressionLevel = ZipArchiveFormat.outputEntryCompressionLevel;
                if (compressionLevel == null) {
                    compressionLevel = ZipArchiveFormat.enumValueOf(
                        CompressionLevel.class,
                        System.getProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_COMPRESSION_LEVEL)
                    );
                }

                boolean encrypt = (
                    ZipArchiveFormat.outputEntryEncrypt
                    || Boolean.getBoolean(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPT)
                );

                EncryptionMethod encryptionMethod = ZipArchiveFormat.outputEntryEncryptionMethod;
                if (encryptionMethod == null) {
                    encryptionMethod = ZipArchiveFormat.enumValueOf(
                        EncryptionMethod.class,
                        System.getProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPTION_METHOD)
                    );
                }

                this.putArchiveEntry(entry, compressionLevel, encrypt, encryptionMethod);
            }

            public void
            putArchiveEntry(
                ArchiveEntry               entry,
                @Nullable CompressionLevel compressionLevel,
                boolean                    encrypt,
                @Nullable EncryptionMethod encryptionMethod
            ) throws IOException {

                ZipParameters zipParameters = new ZipParameters();

                // Entry name, size and time stamp.
                zipParameters.setFileNameInZip(entry.getName());
                zipParameters.setEntrySize(entry.getSize());
                zipParameters.setLastModifiedFileTime(entry.getLastModifiedDate().getTime());

                // Entry compression level.
                if (compressionLevel != null) zipParameters.setCompressionLevel(compressionLevel); // Default is NORMAL.

                // Entry encryption.
                zipParameters.setEncryptFiles(encrypt); // Default is false.

                // Entry encryption method.
                if (encryptionMethod != null) zipParameters.setEncryptionMethod(encryptionMethod);    // Default is NONE.

                zos.putNextEntry(zipParameters);
            }

            @Override public void
            closeArchiveEntry() throws IOException {
                zos.closeEntry();
            }

            @Override public void
            finish() throws IOException {
                zos.close();
            }

            @Override @NotNullByDefault(false) public ArchiveEntry
            createArchiveEntry(File inputFile, String entryName) throws IOException {
                return ZipArchiveFormat.zipArchiveEntry(entryName, inputFile.length(), inputFile.isDirectory(), new Date(inputFile.lastModified()));
            }

            @Override @NotNullByDefault(false) public void
            write(byte[] b, int off, int len) throws IOException { zos.write(b, off, len); }

            @Override public void
            close() throws IOException {
                zos.close();
                os.close();
                super.close();
            }

            @Override public ArchiveFormat
            getArchiveFormat() { return ZipArchiveFormat.get(); }
        };
    }

    @Override public ArchiveOutputStream
    create(File archiveFile) throws IOException { return this.zipArchiveOutputStream(archiveFile); }

    @Override public void
    writeEntry(
        ArchiveOutputStream                                              archiveOutputStream,
        String                                                           name,
        @Nullable Date                                                   lastModifiedDate,
        ConsumerWhichThrows<? super OutputStream, ? extends IOException> writeContents
    ) throws IOException {
        if (!(archiveOutputStream instanceof ZipArchiveOutputStream)) {
            throw new IllegalArgumentException(archiveOutputStream.getClass().getName());
        }

        // Entry names ending in "/" designate DIRECTORIES, so strip all trailing slashes.
        while (name.endsWith("/")) name = name.substring(0, name.length() - 1);

        // ZIP format does not support "no last modified time", so we map that to 0 since the epoch.
        if (lastModifiedDate == null) lastModifiedDate = new Date(0);

        ZipArchiveEntry zae = ZipArchiveFormat.zipArchiveEntry(name, -1, false, lastModifiedDate);
        archiveOutputStream.putArchiveEntry(zae);

        writeContents.consume(archiveOutputStream);

        archiveOutputStream.closeArchiveEntry();
    }

    @Override public void
    writeDirectoryEntry(ArchiveOutputStream archiveOutputStream, String name) throws IOException {
        if (!(archiveOutputStream instanceof ZipArchiveOutputStream)) {
            throw new IllegalArgumentException(archiveOutputStream.getClass().getName());
        }

        // The way to designate a DIRECTORY entry is a trailing slash.
        if (!name.endsWith("/")) name += '/';

        archiveOutputStream.putArchiveEntry(ZipArchiveFormat.zipArchiveEntry(
            name,      // entryName
            -1,        // size
            true,      // isDirectory
            new Date() // lastModifiedDate
        ));
        archiveOutputStream.closeArchiveEntry();
    }

    @Override public void
    writeEntry(
        ArchiveOutputStream                                              archiveOutputStream,
        ArchiveEntry                                                     archiveEntry,
        @Nullable String                                                 name,
        ConsumerWhichThrows<? super OutputStream, ? extends IOException> writeContents
    ) throws IOException {
        if (!(archiveOutputStream instanceof ZipArchiveOutputStream)) {
            throw new IllegalArgumentException(archiveOutputStream.getClass().getName());
        }

        ZipArchiveEntry nzae = ZipArchiveFormat.zipArchiveEntry(name != null ? name : archiveEntry.getName(), archiveEntry.getSize(), false, archiveEntry.getLastModifiedDate());

//        if (archiveEntry instanceof ZipArchiveEntry) {
//            ZipArchiveEntry zae  = (ZipArchiveEntry) archiveEntry;
//
//            nzae.setComment(zae.getComment());
//            nzae.setExternalAttributes(zae.getExternalAttributes());
//            nzae.setExtraFields(zae.getExtraFields(true));
//            nzae.setGeneralPurposeBit(zae.getGeneralPurposeBit());
//            nzae.setInternalAttributes(zae.getInternalAttributes());
//            nzae.setMethod(zae.getMethod());
//            if (nzae.isDirectory()) {
//                nzae.setSize(0);
//                nzae.setCrc(0);
//            }
//        }

        if (archiveEntry.isDirectory()) {
            archiveOutputStream.putArchiveEntry(nzae);
        } else
        {//if (nzae.getMethod() != ZipArchiveOutputStream.STORED) {
            archiveOutputStream.putArchiveEntry(nzae);
            writeContents.consume(archiveOutputStream);
//        } else
//        {
//
//            // Work around
//            //    java.util.zip.ZipException: uncompressed size is required for STORED method when not writing to a file
//            //    java.util.zip.ZipException: crc checksum is required for STORED method when not writing to a file
//            final Pipe ep = PipeFactory.elasticPipe();
//            try {
//                CRC32 crc32 = new CRC32();
//
//                // Copy the entry contents to the elastic pipe, and, at the same time, count the bytes and compute
//                // the CRC32.
//                long uncompressedSize = OutputStreams.writeAndCount(writeContents, OutputStreams.tee(
//                    OutputStreams.updatesChecksum(crc32),
//                    new OutputStream() {
//
//                        @Override public void
//                        write(int b) throws IOException { this.write(new byte[] { (byte) b }, 0, 1); }
//
//                        @NotNullByDefault(false) @Override public void
//                        write(byte[] b, int off, int len) throws IOException {
//                            while (len > 0) {
//                                int x = ep.write(b, off, len);
//                                assert x > 0;
//                                off += x;
//                                len -= x;
//                            }
//                        }
//                    }
//                ));
//
//                nzae.setSize(uncompressedSize);
//                nzae.setCrc(crc32.getValue());
//                archiveOutputStream.putArchiveEntry(nzae);
//
//                byte[] buffer = new byte[8192];
//                while (!ep.isEmpty()) {
//                    int n = ep.read(buffer);
//                    archiveOutputStream.write(buffer, 0, n);
//                }
//
//                ep.close();
//            } finally {
//                try { ep.close(); } catch (Exception e) {}
//            }
        }

        archiveOutputStream.closeArchiveEntry();
    }

    @Override public boolean
    matches(byte[] signature, int signatureLength) {
        return org.apache.commons.compress.archivers.zip.ZipArchiveInputStream.matches(signature, signatureLength);
    }

    @Override @Nullable public String
    getCompressionMethod(ArchiveEntry ae) {
        return ((ZipArchiveEntry) ae).method;
    }

    /**
     * Uses the password specified in the system property {@value ZipArchiveFormat#SYSTEM_PROPERTY_OUTPUT_FILE_PASSWORD}
     * to encrypt all archive entries that will be created throught the returned {@link ArchiveOutputStream}.
     */
    private ArchiveOutputStream
    zipArchiveOutputStream(File archiveFile) {

        char[] opcs = ZipArchiveFormat.outputPasswordChars;
        if (opcs == null) opcs = ZipArchiveFormat.toCharArray(System.getProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_FILE_PASSWORD));

        return this.zipArchiveOutputStream(archiveFile, opcs);
    }

    /**
     * Uses the given <var>password</var> to encrypt all archive entries that will be created throught the returned
     * {@link ArchiveOutputStream}.
     */
    private ArchiveOutputStream
    zipArchiveOutputStream(File archiveFile, @Nullable char[] password) {

        final ZipFile zipFile = new ZipFile(archiveFile, password);

        return new ZipArchiveOutputStream() {

            @Nullable ZipParameters         zipParameters;
            @Nullable ByteArrayOutputStream baos;

            @Override @NotNullByDefault(false) public void
            putArchiveEntry(ArchiveEntry entry) throws IOException {

                CompressionLevel compressionLevel = ZipArchiveFormat.outputEntryCompressionLevel;
                if (compressionLevel == null) {
                    compressionLevel = ZipArchiveFormat.enumValueOf(
                        CompressionLevel.class,
                        System.getProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_COMPRESSION_LEVEL)
                    );
                }

                boolean encrypt = (
                    ZipArchiveFormat.outputEntryEncrypt
                    || Boolean.getBoolean(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPT)
                );

                EncryptionMethod encryptionMethod = ZipArchiveFormat.outputEntryEncryptionMethod;
                if (encryptionMethod == null) {
                    encryptionMethod = ZipArchiveFormat.enumValueOf(
                        EncryptionMethod.class,
                        System.getProperty(ZipArchiveFormat.SYSTEM_PROPERTY_OUTPUT_ENTRY_ENCRYPTION_METHOD)
                    );
                }

                this.putArchiveEntry(entry, compressionLevel, encrypt, encryptionMethod);
            }

            public void
            putArchiveEntry(
                ArchiveEntry               entry,
                @Nullable CompressionLevel compressionLevel,
                boolean                    encrypt,
                @Nullable EncryptionMethod encryptionMethod
            ) throws IOException {

                ZipParameters zps = this.zipParameters = new ZipParameters();

                // Entry name and time stamp.
                zps.setFileNameInZip(entry.getName());
                zps.setLastModifiedFileTime(entry.getLastModifiedDate().getTime());

                // Entry compression level.
                if (compressionLevel != null) zps.setCompressionLevel(compressionLevel); // Default is NORMAL.

                // Entry encryption.
                zps.setEncryptFiles(encrypt); // Default is false.

                // Entry encryption method.
                if (encryptionMethod != null) zps.setEncryptionMethod(encryptionMethod);    // Default is NONE.

                this.baos = new ByteArrayOutputStream();
            }

            @Override public void
            closeArchiveEntry() throws IOException {

                ZipParameters zps = this.zipParameters;
                if (zps == null) return;

                assert this.baos != null;
                zipFile.addStream(new ByteArrayInputStream(this.baos.toByteArray()), zps);

                this.zipParameters = null;
                this.baos          = null;
            }

            @Override public void
            finish() throws IOException {
                ;
            }

            @Override @NotNullByDefault(false) public ArchiveEntry
            createArchiveEntry(File inputFile, String entryName) throws IOException {
                return new ZipArchiveEntry() {
                    @Override public String  getName()             { return entryName;                          }
                    @Override public long    getSize()             { return inputFile.length();                 }
                    @Override public boolean isDirectory()         { return inputFile.isDirectory();            }
                    @Override public Date    getLastModifiedDate() { return new Date(inputFile.lastModified()); }
                    @Override public String  toString()            { return entryName;                          }

                };
            }

            @Override @NotNullByDefault(false) public void
            write(byte[] b, int off, int len) throws IOException {
                assert this.baos != null;
                this.baos.write(b, off, len);
            }

            @Override public void
            close() throws IOException {
                zipFile.close();
                super.close();
            }

            @Override public ArchiveFormat
            getArchiveFormat() { return ZipArchiveFormat.get(); }
        };
    }

    private static ZipArchiveEntry
    zipArchiveEntry(AbstractFileHeader afh) {

        long uncompressedSize = afh.getUncompressedSize();

        // Zip4j appears to use "0" as the representation for "unknown", as opposed to prg.apache.commons.compress.
        if (uncompressedSize == 0) uncompressedSize = ArchiveEntry.SIZE_UNKNOWN;

        ZipArchiveEntry result = ZipArchiveFormat.zipArchiveEntry(
            afh.getFileName(),                       // entryName
            uncompressedSize,                        // size
            afh.isDirectory(),                       // isDirectory
            new Date(afh.getLastModifiedTimeEpoch()) // lastModifiedTime
        );

        // "java.util.zip.ZipEntry" calls them "STORED" and "DEFLATED", but "net.lingala.zip4j.model.enums.CompressionMethod"
        // calls them "STORE" and "DEFLATE" (without the trailing "D").
        result.method = afh.getCompressionMethod().name() + "D";
        return result;
    }

    private static ZipArchiveEntry
    zipArchiveEntry(String entryName, long size, boolean isDirectory, Date lastModifiedDate) {
        assert entryName        != null;
        assert lastModifiedDate != null;

        return new ZipArchiveEntry() {
            @Override public String  getName()             { return entryName;        }
            @Override public long    getSize()             { return size;             }
            @Override public boolean isDirectory()         { return isDirectory;      }
            @Override public Date    getLastModifiedDate() { return lastModifiedDate; }
            @Override public String  toString()            { return entryName;        }
        };
    }

    private static abstract
    class ZipArchiveOutputStream extends ArchiveOutputStream2 {}

    private static abstract
    class ZipArchiveInputStream extends ArchiveInputStream {}

    private static abstract
    class ZipArchiveEntry implements ArchiveEntry {
        @Nullable String method;
    }

    @Override public String
    toString() { return this.getName(); }

    /**
     * @throws IllegalArgumentException
     */
    @Nullable private static <E extends Enum<E>> E
    enumValueOf(Class<E> enumClass, @Nullable String string) {

        if (string == null) return null;

        return Enum.valueOf(enumClass, string.toUpperCase());
    }

    public static void
    setOutputEntryCompressionLevel(CompressionLevel value) { ZipArchiveFormat.outputEntryCompressionLevel = value; }

    public static void
    setInputFilePasswordChars(char[] inputPasswordChars) { ZipArchiveFormat.inputPasswordChars = inputPasswordChars; }

    public static void
    setOutputFilePasswordChars(char[] outputPasswordChars) { ZipArchiveFormat.outputPasswordChars = outputPasswordChars; }

    public static void
    setOutputEntryEncrypt(boolean value) { ZipArchiveFormat.outputEntryEncrypt = value; }

    /**
     * Sets the encryption method for all zip output entries that will be created afterwards.
     */
    public static void
    setOutputEntryEncryptionMethod(EncryptionMethod value) { ZipArchiveFormat.outputEntryEncryptionMethod = value; }

    @Nullable private static char[]
    toCharArray(@Nullable String string) { return string == null ? null : string.toCharArray(); }
}
