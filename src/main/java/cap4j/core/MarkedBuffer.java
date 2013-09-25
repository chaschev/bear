/*
 * Copyright (C) 2013 Andrey Chaschev.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cap4j.core;

import java.util.HashMap;
import java.util.Map;

/**
* User: chaschev
* Date: 8/25/13
*/
public class MarkedBuffer {
    private final boolean stdErr;
    byte[] bytes;
    int startPosition = 0;
    int interimPosition = 0;

    Map<String, Integer> marks = new HashMap<String, Integer>();

    public MarkedBuffer(boolean stdErr) {
        this.stdErr = stdErr;
    }

    public void markStart(){
        startPosition = bytes.length;
    }

    public void markInterim(){
        interimPosition =  bytes.length;
    }

    void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public void progress(byte[] bytes) {
        this.bytes = bytes;
    }

    public String interimText() {
        return new String(bytes, interimPosition, bytes.length - interimPosition);
    }

    public String wholeText() {
        return new String(bytes, startPosition, bytes.length - startPosition);
    }

    public void putMark(String name){
        marks.put(name, bytes.length);
    }

    public String subText(String mark1, String mark2){
        return new String(bytes, markToPosition(mark1), markToPosition(mark2));
    }

    private int markToPosition(String mark) {
        int pos;

        if("start".equals(mark)){
            pos = startPosition;
        }else
        if("interim".equals(mark)){
            pos = interimPosition;
        }else{
            pos = marks.get(mark);
        }
        return pos;

    }

    public int length() {
        return bytes == null ? 0 : bytes.length;
    }

    public boolean isStdErr() {
        return stdErr;
    }
}
