/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.microsoft.rtf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.io.input.TaggedInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * RTF parser
 */
public class RTFParser implements Parser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -4165069489372320313L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("rtf"));
    /**
     * maximum number of bytes per embedded object/pict (default: 20MB)
     */
    private static int EMB_OBJ_MAX_BYTES = 20 * 1024 * 1024; //20MB
    //get rid of this once we get rid of the other static maxbytes...
    private static volatile boolean USE_STATIC = false;
    @Field
    private int memoryLimitInKb = EMB_OBJ_MAX_BYTES / 1024;
    @Field
    private boolean ignoreListMarkup = false;

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        metadata.set(Metadata.CONTENT_TYPE, "application/rtf");
        TaggedInputStream tagged = new TaggedInputStream(stream);
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        try {
            parseInline(stream, xhtml, metadata, context);
        } catch (IOException e) {
            tagged.throwIfCauseOf(e);
            throw new TikaException("Error parsing an RTF document", e);
        } finally {
            xhtml.endDocument();
        }
    }

    /**
     * This bypasses wrapping the handler for inline parsing (in at least the OutlookExtractor).
     *
     * @param is
     * @param handler
     * @param metadata
     * @param context
     * @throws TikaException
     * @throws IOException
     * @throws SAXException
     */
    public void parseInline(InputStream is, ContentHandler handler, Metadata metadata, ParseContext context)
            throws TikaException, IOException, SAXException {
        RTFEmbObjHandler embObjHandler =
                new RTFEmbObjHandler(handler, metadata, context, getMemoryLimitInKb());
        final TextExtractor ert = new TextExtractor(handler, metadata, embObjHandler);
        ert.setIgnoreListMarkup(ignoreListMarkup);
        ert.extract(is);
    }

    public int getMemoryLimitInKb() {
        //there's a race condition here, but it shouldn't matter.
        if (USE_STATIC) {
            if (EMB_OBJ_MAX_BYTES < 0) {
                return EMB_OBJ_MAX_BYTES;
            }
            return EMB_OBJ_MAX_BYTES / 1024;
        }
        return memoryLimitInKb;
    }

    @Field
    public void setMemoryLimitInKb(int memoryLimitInKb) {
        this.memoryLimitInKb = memoryLimitInKb;
        USE_STATIC = false;
    }
}
