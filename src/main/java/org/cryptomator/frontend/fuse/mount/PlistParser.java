package org.cryptomator.frontend.fuse.mount;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * SAX parser for Apple's Property List (.plist) files.
 *
 * @see <a href="http://www.apple.com/DTDs/PropertyList-1.0.dtd">PropertyList-1.0.dtd</a>
 */
class PlistParser {

	private static final String ELEM_PLIST = "plist";
	private static final String ELEM_DICT = "dict";

	private static final String ATTR_VERSION = "version";
	private static final String SUPPORTED_PLIST_VERSION = "1.0";

	private final XMLStreamReader reader;
	private Plist.Value dict;

	public PlistParser(XMLStreamReader reader) {
		this.reader = reader;
	}

	public Plist parse() throws XMLStreamException {
		while (reader.hasNext()) {
			reader.next();
			switch (reader.getEventType()) {
				case XMLStreamConstants.START_ELEMENT:
					startElement();
					break;
				case XMLStreamConstants.END_DOCUMENT:
					if (dict == null) {
						throw new XMLStreamException("Didn't find any <dict>.");
					}
					break;
				default:
					break;
			}
		}
		return new Plist(dict.getDict());
	}

	private void startElement() throws XMLStreamException {
		String element = reader.getLocalName().toLowerCase();
		switch (element) {
			case ELEM_PLIST:
				validatePlistVersion();
				break;
			case ELEM_DICT:
				dict = new PlistDictParser(reader).parse();
				break;
			default:
				throw new XMLStreamException("Unexpected element: " + element);
		}
	}

	private void validatePlistVersion() throws UnsupportedPlistException {
		String firstAttrName = reader.getAttributeLocalName(0);
		String firstAttrValue = reader.getAttributeValue(0);
		if (!ATTR_VERSION.equalsIgnoreCase(firstAttrName) || !SUPPORTED_PLIST_VERSION.equals(firstAttrValue)) {
			throw new UnsupportedPlistException("Unsupported plist, expected version=\"1.0\", but found " + firstAttrName + "=\"" + firstAttrValue + "\"");
		}
	}

	public static class UnsupportedPlistException extends XMLStreamException {

		private UnsupportedPlistException(String msg) {
			super(msg);
		}
	}

}
