/* kXML 2
 *
 * Copyright (C) 2000, 2001, 2002 
 *               Stefan Haustein
 *               D-46045 Oberhausen (Rhld.),
 *               Germany. All Rights Reserved.
 *
 * The contents of this file are subject to the "Lesser GNU Public
 * License" (LGPL); you may not use this file except in compliance
 * with the License.
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific terms governing rights and limitations
 * under the License.
 * */

package org.kxml2.io;

import java.io.*;
import org.xmlpull.v1.*;

public class KXmlSerializer implements XmlSerializer {

    static final String UNDEFINED = ":";

    private Writer writer;

    private boolean pending;
    private int auto;
    private int depth;

    private String[] elementStack = new String[12]; //nsp/prefix/name
    private int[] nspCounts = new int[4];
    private String[] nspStack = new String[8]; //prefix/nsp
    private boolean[] indent = new boolean[4];

    private final void check() throws IOException {
        if (!pending)
            return;
        writer.write(">");
        pending = false;

        if (nspCounts.length < depth + 2) {
            int[] hlp = new int[depth + 5];
            System.arraycopy(nspCounts, 0, hlp, 0, depth + 1);
            nspCounts = hlp;
        }

        nspCounts[depth + 1] = nspCounts[depth];
    }

    private final void writeEscaped(String s, int quot) throws IOException {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' :
                    writer.write("&amp;");
                    break;
                case '>' :
                    writer.write("&gt;");
                    break;
                case '<' :
                    writer.write("&lt;");
                    break;
                case '"' :
                case '\'' :
                    if (c == quot) {
                        writer.write(c == '"' ? "&quot;" : "&apos;");
                        break;
                    }
                default :
                    writer.write(c);
            }
        }
    }

    /*
    	private final void writeIndent() throws IOException {
    		writer.write("\r\n");
    		for (int i = 0; i < depth; i++)
    			writer.write(' ');
    	}*/


    public void docdecl(String dd) throws IOException{
        writer.write("<!DOCTYPE ");
        writer.write(dd);
        writer.write(">");
    }

    public void endDocument() throws IOException {
        flush();
        if (depth != 0) throw new IllegalStateException ();
    }

    public void entityRef(String name) throws IOException {
        check();
        writer.write('&');
        writer.write(name);
        writer.write(';');
    }

    public boolean getFeature(String name) {
        //return false;
      return ( "http://xmlpull.org/v1/doc/features.html#indent-output".equals(name)) 
        ? indent[depth] : false;
    }


	public String getPrefix (String namespace, boolean create) {
        try {
		  return getPrefix (namespace, false, create);
        }
        catch (IOException e) {
            throw new RuntimeException (e.toString());
        }
	}

    private final String getPrefix(
        String namespace,
        boolean includeDefault,
        boolean create) throws IOException {

        for (int i = nspCounts[depth] * 2 - 2; i >= 0; i -= 2) {
            if (nspStack[i + 1].equals(namespace) && 
                (includeDefault || nspStack [i] != null))
                return nspStack[i];
        }

        if (!create)
            return UNDEFINED;

        String prefix;

        do {
            prefix = "n" + (auto++);
            for (int i = nspCounts[depth] * 2 - 2; i >= 0; i -= 2) {
                if (prefix.equals (nspStack [i])) {
                    prefix = null;
                    break;
                }
            }
        }
        while (prefix == null);

        depth--;
        setPrefix(prefix, namespace);
        depth++;
        return prefix;
    }

    public Object getProperty(String name) {
        throw new RuntimeException("Unsupported property");
    }

    public void ignorableWhitespace(String s) throws IOException {
        text(s);
    }

    public void setFeature(String name, boolean value) {
       if ( "http://xmlpull.org/v1/doc/features.html#indent-output".equals(name)) {
            indent[depth] = value;
        }
        else 
            throw new RuntimeException("Unsupported Feature");
    }

    public void setProperty(String name, Object value) {
        throw new RuntimeException("Unsupported Property:" + value);
    }

    public void setPrefix(String prefix, String namespace) throws IOException{

        check();

        String defined = getPrefix(namespace, true, false);

        if (prefix.equals ("")) prefix = null;

        // boil out if already defined

        if (defined == prefix || (prefix != null && prefix.equals(defined)))
            return;

        int pos = (nspCounts[depth + 1]++) << 1;

        if (nspStack.length < pos + 1) {
            String[] hlp = new String[nspStack.length + 16];
            System.arraycopy(nspStack, 0, hlp, 0, pos);
            nspStack = hlp;
        }

        nspStack[pos++] = prefix;
        nspStack[pos] = namespace;
    }

    public void setOutput(Writer writer) {
        this.writer = writer;

       // elementStack = new String[12]; //nsp/prefix/name
        //nspCounts = new int[4];
        //nspStack = new String[8]; //prefix/nsp
        //indent = new boolean[4];
    

        nspCounts[0] = 2;
        nspCounts[1] = 2;
        nspStack[1] = "";
        nspStack[0] = null;
        nspStack[2] = "xml";
        nspStack[3] = "http://www.w3.org/XML/1998/namespace";
        pending = false;
        auto = 0;
        depth = 0;
        
    }

    public void setOutput(OutputStream os, String encoding)
        throws IOException {
        if (os == null) throw new IllegalArgumentException ();
        setOutput(
            encoding == null
                ? new OutputStreamWriter(os)
                : new OutputStreamWriter(os, encoding));
    }

    public void startDocument(String encoding, Boolean standalone)
        throws IOException {
        writer.write("<?xml version='1.0' ");
        if (encoding != null) {
        	writer.write ("encoding='");
        	writer.write (encoding);
        	writer.write ("' ");
        }
        if (standalone != null) {
        	writer.write ("encoding='");
        	writer.write (standalone.booleanValue() ? "yes" : "no");
        	writer.write ("' ");
        }
        writer.write ("?>");
    }

    public XmlSerializer startTag(String namespace, String name) throws IOException {
        check();
        
        if (namespace==null) namespace="";
        
        if (indent[depth]) {
            writer.write("\r\n");
            for (int i = 0; i < depth; i++)
                writer.write(' ');
        }

        int esp = depth * 3;

        if (elementStack.length < esp + 3) {
            String[] hlp = new String[elementStack.length + 12];
            System.arraycopy(elementStack, 0, hlp, 0, esp);
            elementStack = hlp;
        }

        depth++;

        if (indent.length <= depth) {
            boolean[] hlp = new boolean[depth + 4];
            System.arraycopy(indent, 0, hlp, 0, depth);
            indent = hlp;
        }
        indent[depth] = indent[depth - 1];

        String prefix = getPrefix(namespace, true, true);

        elementStack[esp++] = namespace;
        elementStack[esp++] = prefix;
        elementStack[esp] = name;

        writer.write('<');
        if (prefix != null) {
            writer.write(prefix);
            writer.write(':');
        }

        writer.write(name);

        for (int i = nspCounts[depth - 1]; i < nspCounts[depth]; i++) {
            writer.write(' ');
            writer.write("xmlns");
            if (nspStack[i * 2] != null) {
                writer.write(':');
                writer.write(nspStack[i * 2]);
            }
            writer.write("=\"");
            writeEscaped(nspStack[i * 2 + 1], '"');
            writer.write('"');
        }

        pending = true;

        return this;
    }

    public XmlSerializer attribute(String namespace, String name, String value)
        throws IOException {
        if (!pending)
            throw new IllegalStateException("illegal position for attribute");

        int cnt = nspCounts[depth];
        if (namespace==null) namespace="";

        String prefix = (namespace == null || "".equals (namespace)) 
        	? null 
            : getPrefix(namespace, false, true);

        if (cnt != nspCounts[depth]) {
            writer.write(' ');
            writer.write("xmlns");
            if (nspStack[cnt * 2] != null) {
                writer.write(':');
                writer.write(nspStack[cnt * 2]);
            }
            writer.write("=\"");
            writeEscaped(nspStack[cnt * 2 + 1], '"');
            writer.write('"');
        }

        writer.write(' ');
        if (prefix != null) {
            writer.write(prefix);
            writer.write(':');
        }
        writer.write(name);
        writer.write('=');
        char q = value.indexOf('"') == -1 ? '"' : '\'';
        writer.write(q);
        writeEscaped(value, '"');
        writer.write(q);

        return this;
    }

    public void flush() throws IOException {
        check();
        writer.flush();
    }
    /*
    	public void close() throws IOException {
    		check();
    		writer.close();
    	}
    */
    public XmlSerializer endTag(String namespace, String name) throws IOException {

        depth--;
        if (namespace==null) namespace="";

        if (!elementStack[depth * 3].equals(namespace)
            || !elementStack[depth * 3 + 2].equals(name))
            throw new IllegalArgumentException("start/end tag mismatch");

        if (pending) {
            writer.write(" />");
            pending = false;
        }
        else {
            if (indent[depth + 1]) {
                writer.write("\r\n");
                for (int i = 0; i < depth; i++)
                    writer.write(' ');
            }

            writer.write("</");
            String prefix = elementStack[depth * 3 + 1];
            if (prefix != null) {
                writer.write(prefix);
                writer.write(':');
            }
            writer.write(name);
            writer.write('>');
        }

        nspCounts[depth + 1] = nspCounts[depth];
        return this;
    }

    public XmlSerializer text(String text) throws IOException {
        check();
        indent[depth] = false;
        writeEscaped(text, -1);
        return this;
    }

    public XmlSerializer text(char[] text, int start, int len) throws IOException {
        text(new String(text, start, len));
        return this;
    }

    public void cdsect(String data) throws IOException {
        check();
        writer.write("<![CDSECT");
        writer.write(data);
        writer.write("]>");
    }

    public void comment(String comment) throws IOException {
        check();
        writer.write("<!--");
        writer.write(comment);
        writer.write("-->");
    }

    public void processingInstruction(String pi) throws IOException {
        check();
        writer.write("<?");
        writer.write(pi);
        writer.write('>');
    }
}
