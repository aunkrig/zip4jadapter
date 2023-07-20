
# An alternative implementation of the "zip" archive format, based on the ZIP4J library.

This implementation supports encryption, which is controlled by a set of system properties:

* zip4j.inputFilePassword -- used to decrypt zip input files
* zip4j.outputEntryCompressionLevel -- used to configure the compression level of all output zip entries created
  afterwards
* zip4j.outputEntryEncrypt -- used to enable output file encryption
* zip4j.outputEntryEncryptionMethod -- used to encrypt zip output file entries
* zip4j.outputFilePassword -- used to encrypt zip output files
