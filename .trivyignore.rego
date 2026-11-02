package trivy

import rego.v1

default ignore := false

# libexpat is a native OS library in the base image that the JVM never loads.
# Java XML parsing uses the JDK's pure-Java parsers.
ignore if {
	input.PkgName == "libexpat"
	startswith(input.PkgIdentifier.PURL, "pkg:apk/")
}
