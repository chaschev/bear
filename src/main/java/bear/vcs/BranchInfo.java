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

package bear.vcs;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class BranchInfo extends CommandLineResult<BranchInfo> {
    public String author;
    public String revision;
    public String date;

    BranchInfo() {
    }

    public BranchInfo(String author, String revision, String date) {
        super("branch info", null);
        this.author = author;
        this.revision = revision;
        this.date = date;
    }
}
