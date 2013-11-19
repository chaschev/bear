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

package bear.console;

import chaschev.util.Exceptions;
import com.google.common.base.Function;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class ConsolesDivider<ENTRY> extends GroupDivider<ENTRY> {
    protected final List<? extends AbstractConsole> consoles;

    private final List<ListenableFuture<ENTRY>> futures;

    protected Future<EqualityGroups> groups;

    public ConsolesDivider(
        List<ENTRY> entries, List<ListenableFuture<ENTRY>> futures, List<? extends AbstractConsole> consoles,
        Function<ENTRY, String> entryAsText, Function<ENTRY, String> entryId) {
        super(entries, entryId, entryAsText);

        this.consoles = consoles;
        this.futures = futures;
    }

    public void await(int sec) {
        try {
            Futures.successfulAsList(futures).get(sec, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw Exceptions.runtime(e);
        }
    }


    public List<ListenableFuture<ENTRY>> getFutures() {
        return futures;
    }

    public List<? extends AbstractConsole> getConsoles() {
        return consoles;
    }

    public List<ArrivedEntry<ENTRY>> getArrivedEntries() {
        return arrivedEntries;
    }

}
