/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.xml.bind.v2.runtime.output;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Constructor;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.sun.xml.bind.marshaller.CharacterEscapeHandler;
import com.sun.xml.bind.v2.runtime.JAXBContextImpl;
import com.sun.xml.bind.v2.runtime.XMLSerializer;

import org.xml.sax.SAXException;

/**
 * {@link XmlOutput} that writes to StAX {@link XMLStreamWriter}.
 * <p>
 * TODO:
 * Finding the optimized FI implementations is a bit hacky and not very
 * extensible. Can we use the service provider mechanism in general for 
 * concrete implementations of XmlOutputAbstractImpl.
 * 
 * @author Kohsuke Kawaguchi
 */
public class XMLStreamWriterOutput extends XmlOutputAbstractImpl {

    /**
     * Creates a new {@link XmlOutput} from a {@link XMLStreamWriter}.
     * This method recognizes an FI StAX writer.
     */
    public static XmlOutput create(XMLStreamWriter out, JAXBContextImpl context, CharacterEscapeHandler escapeHandler) {
        // try optimized path
        final Class writerClass = out.getClass();
        if (writerClass==FI_STAX_WRITER_CLASS) {
            try {
                return FI_OUTPUT_CTOR.newInstance(out, context);
            } catch (Exception e) {
            }  
        } 
        if (STAXEX_WRITER_CLASS!=null && STAXEX_WRITER_CLASS.isAssignableFrom(writerClass)) {
            try {
                return STAXEX_OUTPUT_CTOR.newInstance(out);
            } catch (Exception e) {
            }
        }

        CharacterEscapeHandler xmlStreamEscapeHandler = escapeHandler != null ?
                escapeHandler : NewLineEscapeHandler.theInstance;

        // otherwise the normal writer.
        return new XMLStreamWriterOutput(out, xmlStreamEscapeHandler);
    }


    private final XMLStreamWriter out;

    private final CharacterEscapeHandler escapeHandler;

    private final XmlStreamOutWriterAdapter writerWrapper;

    protected final char[] buf = new char[256];

    protected XMLStreamWriterOutput(XMLStreamWriter out, CharacterEscapeHandler escapeHandler) {
        this.out = out;
        this.escapeHandler = escapeHandler;
        this.writerWrapper = new XmlStreamOutWriterAdapter(out);
    }

    // not called if we are generating fragments
    @Override
    public void startDocument(XMLSerializer serializer, boolean fragment, int[] nsUriIndex2prefixIndex, NamespaceContextImpl nsContext) throws IOException, SAXException, XMLStreamException {
        super.startDocument(serializer, fragment,nsUriIndex2prefixIndex,nsContext);
        if(!fragment)
            out.writeStartDocument();
    }

    @Override
    public void endDocument(boolean fragment) throws IOException, SAXException, XMLStreamException {
        if(!fragment) {
            out.writeEndDocument();
            out.flush();
        }
        super.endDocument(fragment);
    }

    public void beginStartTag(int prefix, String localName) throws IOException, XMLStreamException {
        out.writeStartElement(
            nsContext.getPrefix(prefix),
            localName,
            nsContext.getNamespaceURI(prefix));

        NamespaceContextImpl.Element nse = nsContext.getCurrent();
        if(nse.count()>0) {
            for( int i=nse.count()-1; i>=0; i-- ) {
                String uri = nse.getNsUri(i);
                if(uri.length()==0 && nse.getBase()==1)
                    continue;   // no point in definint xmlns='' on the root
                out.writeNamespace(nse.getPrefix(i),uri);
            }
        }
    }

    public void attribute(int prefix, String localName, String value) throws IOException, XMLStreamException {
        if(prefix==-1)
            out.writeAttribute(localName,value);
        else
            out.writeAttribute(
                    nsContext.getPrefix(prefix),
                    nsContext.getNamespaceURI(prefix),
                    localName, value);
    }

    public void endStartTag() throws IOException, SAXException {
        // noop
    }

    public void endTag(int prefix, String localName) throws IOException, SAXException, XMLStreamException {
        out.writeEndElement();
    }

    public void text(String value, boolean needsSeparatingWhitespace) throws IOException, SAXException, XMLStreamException {
        if(needsSeparatingWhitespace)
            out.writeCharacters(" ");
        escapeHandler.escape(value.toCharArray(), 0, value.length(), false, writerWrapper);
    }

    public void text(Pcdata value, boolean needsSeparatingWhitespace) throws IOException, SAXException, XMLStreamException {
        if(needsSeparatingWhitespace)
            out.writeCharacters(" ");

        int len = value.length();
        if(len <buf.length) {
            value.writeTo(buf,0);
            out.writeCharacters(buf,0,len);
        } else {
            out.writeCharacters(value.toString());
        }
    }

    /**
     * Reference to FI's XMLStreamWriter class, if FI can be loaded.
     */
    private static final Class FI_STAX_WRITER_CLASS = initFIStAXWriterClass();
    private static final Constructor<? extends XmlOutput> FI_OUTPUT_CTOR = initFastInfosetOutputClass();

    private static Class initFIStAXWriterClass() {
        try {
            Class<?> llfisw = Class.forName("org.jvnet.fastinfoset.stax.LowLevelFastInfosetStreamWriter");
            Class<?> sds = Class.forName("com.sun.xml.fastinfoset.stax.StAXDocumentSerializer");
            // Check if StAXDocumentSerializer implements LowLevelFastInfosetStreamWriter
            if (llfisw.isAssignableFrom(sds))
                return sds;
            else
                return null;
        } catch (Throwable e) {
            return null;
        }
    }

    private static Constructor<? extends XmlOutput> initFastInfosetOutputClass() {
        try {
            if (FI_STAX_WRITER_CLASS == null)
                return null;
            Class c = Class.forName("com.sun.xml.bind.v2.runtime.output.FastInfosetStreamWriterOutput");
            return c.getConstructor(FI_STAX_WRITER_CLASS, JAXBContextImpl.class);
        } catch (Throwable e) {
            return null;
        }
    }
    
    //
    // StAX-ex
    //
    private static final Class STAXEX_WRITER_CLASS = initStAXExWriterClass();
    private static final Constructor<? extends XmlOutput> STAXEX_OUTPUT_CTOR = initStAXExOutputClass();

    private static Class initStAXExWriterClass() {
        try {
            return Class.forName("org.jvnet.staxex.XMLStreamWriterEx");
        } catch (Throwable e) {
            return null;
        }
    }

    private static Constructor<? extends XmlOutput> initStAXExOutputClass() {
        try {
            Class c = Class.forName("com.sun.xml.bind.v2.runtime.output.StAXExStreamWriterOutput");
            return c.getConstructor(STAXEX_WRITER_CLASS);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * Performs character escaping only for new lines.
     */
    private static class NewLineEscapeHandler implements CharacterEscapeHandler {

        public static final NewLineEscapeHandler theInstance = new NewLineEscapeHandler();

        @Override
        public void escape(char[] ch, int start, int length, boolean isAttVal, Writer out) throws IOException {
            int limit = start+length;
            int lastEscaped = start;
            for (int i = start; i < limit; i++) {
                char c = ch[i];
                if (c == '\r' || c == '\n') {
                    if (i != lastEscaped) {
                        out.write(ch, lastEscaped, i - lastEscaped);
                    }
                    lastEscaped = i + 1;
                    if (out instanceof XmlStreamOutWriterAdapter) {
                        try {
                            ((XmlStreamOutWriterAdapter)out).writeEntityRef("#x" + Integer.toHexString(c));
                        } catch (XMLStreamException e) {
                            throw new IOException("Error writing xml stream", e);
                        }
                    } else {
                        out.write("&#x");
                        out.write(Integer.toHexString(c));
                        out.write(';');
                    }
                }
            }

            if (lastEscaped != limit) {
                out.write(ch, lastEscaped, length - lastEscaped);
            }
        }
    }

    private static final class XmlStreamOutWriterAdapter extends Writer {

        private final XMLStreamWriter writer;

        private XmlStreamOutWriterAdapter(XMLStreamWriter writer) {
            this.writer = writer;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            try {
                writer.writeCharacters(cbuf, off, len);
            } catch (XMLStreamException e) {
                throw new IOException("Error writing XML stream", e);
            }
        }

        public void writeEntityRef(String entityReference) throws XMLStreamException {
            writer.writeEntityRef(entityReference);
        }

        @Override
        public void flush() throws IOException {
            try {
                writer.flush();
            } catch (XMLStreamException e) {
                throw new IOException("Error flushing XML stream", e);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                writer.close();
            } catch (XMLStreamException e) {
                throw new IOException("Error closing XML stream", e);
            }
        }
    }
}
