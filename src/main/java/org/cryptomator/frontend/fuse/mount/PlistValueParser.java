package org.cryptomator.frontend.fuse.mount;

import org.cryptomator.frontend.fuse.mount.Plist.ValueType;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

class PlistValueParser {

	private static final String ELEM_DATA = "data";
	private static final String ELEM_DATE = "date";
	private static final String ELEM_REAL = "real";
	private static final String ELEM_INTEGER = "integer";
	private static final String ELEM_STRING = "string";
	private static final String ELEM_TRUE = "true";
	private static final String ELEM_FALSE = "false";

	private final XMLStreamReader reader;
	private final ValueType type;
	private final StringBuilder characterBuffer;

	/**
	 * @param reader A {@link XMLStreamReader} currently at the beginning of a new primitive value (data, date, real, integer, string, true, false) element.
	 * @throws IllegalStateException If the reader is not currently positioned at the beginning of a primitive value element.
	 */
	public PlistValueParser(XMLStreamReader reader) throws IllegalStateException {
		if (reader.getEventType() != XMLStreamConstants.START_ELEMENT ) {
			throw new IllegalStateException("Reader not at beginning of a new element.");
		}
		this.type = getExpectedElementType(reader.getLocalName());
		this.characterBuffer = new StringBuilder();
		this.reader = reader;
	}
	
	private static ValueType getExpectedElementType(String localName) {
		switch (localName.toLowerCase()) {
			case ELEM_STRING:
				return ValueType.STRING;
			case ELEM_DATA:
				return ValueType.DATA;
			case ELEM_DATE:
				return ValueType.DATE;
			case ELEM_INTEGER:
				return ValueType.INTEGER;
			case ELEM_REAL:
				return ValueType.REAL;
			case ELEM_TRUE:
				return ValueType.TRUE;
			case ELEM_FALSE:
				return ValueType.FALSE;
			default:
				throw new IllegalStateException("Unexpected element <" + localName + ">.");
		}
	}

	/**
	 * Proceeds parsing till the end of the current value element.
	 * @return The parsed value
	 * @throws XMLStreamException
	 */
	public Plist.Value parse() throws XMLStreamException {
		while (reader.hasNext()) {
			reader.next();
			switch (reader.getEventType()) {
				case XMLStreamConstants.CHARACTERS:
					appendCharacters();
					break;
				case XMLStreamConstants.END_ELEMENT:
					Object value = endElement();
					return new Plist.Value(type, value);
				default:
					break;
			}
		}
		throw new IllegalStateException("Element not ended");
	}

	private void appendCharacters() {
		characterBuffer.append(reader.getText());
	}

	private Object endElement() throws XMLStreamException {
		String element = reader.getLocalName().toLowerCase();
		switch (element) {
			case ELEM_STRING:
				return characterBuffer.toString();
			case ELEM_TRUE:
				return Boolean.TRUE;
			case ELEM_FALSE:
				return Boolean.FALSE;
			case ELEM_DATA:
				// TODO
			case ELEM_DATE:
				// TODO
			case ELEM_INTEGER:
				// TODO
			case ELEM_REAL:
				// TODO
			default:
				throw new XMLStreamException("Unexpected primitive value: " + element);
		}
	}

}
