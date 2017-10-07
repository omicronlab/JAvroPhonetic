/*
    =============================================================================
    *****************************************************************************
    The contents of this file are subject to the Mozilla Public License
    Version 1.1 (the "License"); you may not use this file except in
    compliance with the License. You may obtain a copy of the License at
    http://www.mozilla.org/MPL/

    Software distributed under the License is distributed on an "AS IS"
    basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
    License for the specific language governing rights and limitations
    under the License.

    The Original Code is JAvroPhonetic

    The Initial Developer of the Original Code is
    Rifat Nabi <to.rifat@gmail.com>

    Copyright (C) OmicronLab (http://www.omicronlab.com). All Rights Reserved.


    Contributor(s): Mohsin Uddin <mohudd55@gmail.com>

    *****************************************************************************
    =============================================================================
*/

package com.omicronlab.avro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.omicronlab.avro.exception.NoPhoneticLoaderException;
import com.omicronlab.avro.phonetic.*;
import java.util.HashMap;

public class PhoneticParser {

    private static volatile PhoneticParser instance = null;
    private static PhoneticLoader loader = null;
    private static List<Pattern> patterns;
    private static HashMap<String, Pattern> patternMap;
    private static String vowel = "";
    private static String consonant = "";
    private static String casesensitive = "";
    private boolean initialized = false;
    private static int maxPatternLength = 0;
    
    // Prevent initialization
    private PhoneticParser(){
        patterns = new ArrayList<>();
        patternMap = new HashMap<>();
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public static PhoneticParser getInstance() {
        PhoneticParser phoneticParser = PhoneticParser.instance;
        if(phoneticParser == null) {
            synchronized (PhoneticParser.class) {
                phoneticParser = PhoneticParser.instance;
                if(phoneticParser == null) {
                    PhoneticParser.instance = phoneticParser = new PhoneticParser();
                }
            }
        }
        return phoneticParser;
    }
    
    public synchronized void setLoader(PhoneticLoader loader) {
        PhoneticParser.loader = loader;
    }

    public synchronized void init() throws Exception {
        if(loader == null) {
            throw new NoPhoneticLoaderException();
        }
        Data data = loader.getData();
        patterns = data.getPatterns();
        Collections.sort(patterns);
        for(Pattern pattern: patterns) {
            patternMap.put(pattern.getFind(), pattern);
        }
        vowel = data.getVowel();
        consonant = data.getConsonant();
        casesensitive = data.getCasesensitive();
        maxPatternLength = patterns.get(0).getFind().length();
        initialized = true;
    }
    
    public String parse(String input) {
        if(initialized == false) {
            try {
                this.init();
            } catch(Exception e) {
                System.err.println(e);
                System.err.println("Please handle the exception by calling init mehotd");
                System.exit(0);
            }
        }
        
        String text = "";
        for(char c: input.toCharArray()) {
            if(this.isCaseSensitive(c)) {
                text += c;
            }
            else {
                text += Character.toLowerCase(c);
            }
        }

        String output = "";
        for(int cur = 0; cur < text.length(); ++cur) {
            int start = cur, end;

            boolean matched = false;
            int len;
            for(len = maxPatternLength; len > 0; --len) {
                end = start + len;
                if(end <= text.length()) {
                    String chunk = text.substring(start, end);
                    Pattern pattern = getMatchedPattern(chunk);
                    if(pattern == null ) {
                    }
                    else {
                         for(Rule rule: pattern.getRules()) {
                            boolean replace = isReplacable (rule,start,end,text);
                            if(replace) {
                                output += rule.getReplace();
                                cur = end - 1;
                                matched = true;
                                break;
                            }
                        }
                        if(matched == true) break;

                        // Default
                        output += pattern.getReplace();
                        cur = end - 1;
                        matched = true;
                        break;
                    } 
                    if(matched == true) break;
                }
            }
            if(!matched) {
                output += text.charAt(cur);
            }
             
        }
        return output;
    }
    
    private Pattern getMatchedPattern (String chunk) {
       Pattern pattern = patternMap.get(chunk);
       return pattern;
    }

    private boolean isReplacable (Rule rule, int start, int end, String fixed) {
        boolean replace = true;
        int chk;
        for (Match match : rule.getMatches()) {
            if(match.getType().equals("suffix")) {
                chk = end;
            }
            // Prefix
            else {
                chk = start - 1;
            }
            // Beginning
            switch (match.getScope()) {
                case "punctuation":
                    if (! (
                            (chk < 0 && match.getType().equals("prefix")) ||
                            (chk >= fixed.length() && match.getType().equals("suffix")) ||
                            this.isPunctuation(fixed.charAt(chk))
                            ) ^ match.isNegative()) {
                        replace = false;
                        break;
                    }
                    break;
                case "vowel":
                    if (! (
                            (
                            (chk >= 0 && match.getType().equals("prefix")) ||
                            (chk < fixed.length() && match.getType().equals("suffix"))
                            ) &&
                            this.isVowel(fixed.charAt(chk))
                            ) ^ match.isNegative()) {
                        replace = false;
                        break;
                    }
                    break;
                case "consonant":
                    if (! (
                            (
                            (chk >= 0 && match.getType().equals("prefix")) ||
                            (chk < fixed.length() && match.getType().equals("suffix"))
                            ) &&
                            this.isConsonant(fixed.charAt(chk))
                            ) ^ match.isNegative()) {
                        replace = false;
                        break;
                    }
                    break;
                case "exact":
                    int s, e;
                    if(match.getType().equals("suffix")) {
                        s = end;
                        e = end + match.getValue().length();
                    }
                    // Prefix
                    else {
                        s = start - match.getValue().length();
                        e = start;
                    }   if (!this.isExact(match.getValue(), fixed, s, e, match.isNegative())) {
                        replace = false;
                        break;
                    }
                    break;
                default:
                    break;
            }
        }
        return replace;
    }
    
    private boolean isVowel(char c) {
        return ((vowel.indexOf(Character.toLowerCase(c)) >= 0));
    }

    private boolean isConsonant(char c) {
        return ((consonant.indexOf(Character.toLowerCase(c)) >= 0));
    }

    private boolean isPunctuation(char c) {
        return (!(this.isVowel(c) || this.isConsonant(c)));
    }

    private boolean isExact(String needle, String heystack, int start, int end, boolean not) {
        return ((start >= 0 && end < heystack.length() && heystack.substring(start, end).equals(needle)) ^ not);
    }

    private boolean isCaseSensitive(char c) {
        return (casesensitive.indexOf(Character.toLowerCase(c)) >= 0);
    }

}