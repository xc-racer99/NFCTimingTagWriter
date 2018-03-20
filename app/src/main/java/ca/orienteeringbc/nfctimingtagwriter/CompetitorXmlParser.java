/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * Created by jon on 12/03/18.
 * Designed to read https://whyjustrun.ca/iof/3.0/organization_list.xml
 * and return the club (short) name and WJR id
 */
package ca.orienteeringbc.nfctimingtagwriter;

import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;

public class CompetitorXmlParser {
    // We don't use namespaces
    private static final String ns = null;

    private List<Competitor> competitors;

    public List<Competitor> parse(InputStream in) throws XmlPullParserException, IOException {
        if (in == null)
            return null;

        competitors = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();

            Log.e("Tag", "Got here 2");

            readFeed(parser);
        } finally {
            if (in != null)
                in.close();
        }
        return competitors;
    }

    private void readFeed(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, "CompetitorList");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            switch (name) {
                case "Competitor":
                    competitors.add(readCompetitor(parser));
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
    }

    // Processes title tags in the feed.
    private int readId(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "Id");
        int id = Integer.parseInt(readText(parser));
        parser.require(XmlPullParser.END_TAG, ns, "Id");
        return id;
    }

    private Competitor readCompetitor(XmlPullParser parser) throws IOException, XmlPullParserException {
        Competitor competitor = null;
        parser.require(XmlPullParser.START_TAG, ns, "Competitor");
        // Id under PersonEntry is registration Id, need Id from Person
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            switch (tagName) {
                case "Person":
                    competitor = readPerson(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Competitor");
        return competitor;
    }

    private Competitor readPerson(XmlPullParser parser) throws IOException, XmlPullParserException {
        Competitor competitor;
        String firstName = null;
        String lastName = null;
        int compId = -1;
        parser.require(XmlPullParser.START_TAG, ns, "Person");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            switch (tagName) {
                case "Id":
                    compId = readId(parser);
                    break;
                case "Name":
                    String[] names = readNameParts(parser);
                    if (names.length == 2) {
                        firstName = names[0];
                        lastName = names[1];
                    }
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        Log.e("Tag", "Got here");
        parser.require(XmlPullParser.END_TAG, ns, "Person");
        competitor = new Competitor(compId, firstName, lastName);
        return competitor;
    }

    private String[] readNameParts(XmlPullParser parser) throws IOException, XmlPullParserException {
        String[] names = new String[2];
        parser.require(XmlPullParser.START_TAG, ns, "Name");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String tagName = parser.getName();
            switch (tagName) {
                case "Given":
                    names[0] = readText(parser);
                    break;
                case "Family":
                    names[1] = readText(parser);
                    break;
                default:
                    skip(parser);
                    break;
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "Name");
        return names;
    }

    // Extracts the text value of a text
    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    // Skips tags the parser isn't interested in. Uses depth to handle nested tags. i.e.,
    // if the next tag after a START_TAG isn't a matching END_TAG, it keeps going until it
    // finds the matching END_TAG (as indicated by the value of "depth" being 0).
    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }
}