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

package bear.core;

import bear.main.event.CellFinishedEventToUI;
import bear.main.event.CommandConsoleEventToUI;
import bear.main.event.TaskConsoleEventToUI;
import bear.main.event.TextConsoleEventToUI;
import bear.session.Address;
import bear.session.DynamicVariable;
import bear.session.SshAddress;
import bear.task.CommandContext;
import bear.task.SessionRunner;
import bear.task.Task;
import bear.task.TaskExecutionContext;
import chaschev.lang.Functions2;
import chaschev.lang.MutableSupplier;
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import static bear.core.SessionContext.ui;
import static com.google.common.base.Functions.forMap;
import static com.google.common.collect.Collections2.transform;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Stage {
    private static final Logger logger = LoggerFactory.getLogger(Stage.class);
    public static final Function<SessionContext, String> SESSION_ID = new Function<SessionContext, String>() {
        public String apply(SessionContext $) {
            return $.id;
        }
    };

    public String name;
    String description;

    final LinkedHashSet<Address> addresses = new LinkedHashSet<Address>();

    GlobalContext global;

    private final MutableSupplier<Stages> stages = new MutableSupplier<Stages>();

    public Stage(String name) {
        this.name = name;
    }

    //todo move this out of here
    public PreparationResult prepareToRun2(BearProject bearSettings) {
        Collection<Address> addresses = global.var(global.bear.addressesForStage).apply(this);

        List<SessionContext> $s = new ArrayList<SessionContext>();

        for (Address address : addresses) {
            final SessionRunner runner = new SessionRunner(null, global);

            SessionContext $ = new SessionContext(global, address, runner);

            $s.add($);
        }

        for (final SessionContext $ : $s) {
            final SessionContext.ExecutionContext execContext = $.getExecutionContext();

            execContext.textAppended.addListener(new DynamicVariable.ChangeListener<String>() {
                public void changedValue(DynamicVariable<String> var, String oldValue, String newValue) {
                    if (StringUtils.isNotEmpty(newValue)) {
                        ui.info(new TextConsoleEventToUI($.getName(), newValue)
                            .setParentId(execContext.currentCommand.getDefaultValue().command.id)
                        );
                    }
                }
            });

            execContext.currentCommand.addListener(new DynamicVariable.ChangeListener<CommandContext>() {
                @Override
                public void changedValue(DynamicVariable<CommandContext> var, CommandContext oldValue, CommandContext newValue) {
                    ui.info(new CommandConsoleEventToUI($.getName(), newValue.toString())
                        .setId(newValue.command.id)
                        .setParentId(execContext.currentTask.getDefaultValue().getId())
                    );
                }
            });

            execContext.currentTask.addListener(new DynamicVariable.ChangeListener<Task>() {
                @Override
                public void changedValue(DynamicVariable<Task> var, Task oldValue, Task newValue) {
                    if ($.getExecutionContext().phaseId.isUndefined()) {
                        return;
                    }

                    String phaseId = $.getExecutionContext().phaseId.getDefaultValue();

                    ui.info(new TaskConsoleEventToUI($.getName(),
                        $.getExecutionContext().phaseName
                            + (newValue.getDefinition() == null ? "" :  " " + newValue.getDefinition().getName())
//                            + " " + phaseId
                        , phaseId)
                        .setId(newValue.getId())
                        .setParentId($.id)
                    );
                }
            });

            execContext.rootExecutionContext.addListener(new DynamicVariable.ChangeListener<TaskExecutionContext>() {
                @Override
                public void changedValue(DynamicVariable<TaskExecutionContext> var, TaskExecutionContext oldValue, TaskExecutionContext newValue) {
                    if (newValue.isFinished()) {
                        ui.info(new CellFinishedEventToUI(newValue.taskResult, newValue.getDuration(), $.getName()));
                    }
                }
            });
        }


        return new PreparationResult($s, bearSettings);
    }

    void addToStages(Stages stages) {
        this.stages.setInstance(stages).makeFinal();
        this.global = stages.global;

        for (Address address : addresses) {
            stages.putAddressToMap(address);
        }
    }

    public Stage add(Address address) {
        addresses.add(address);

        if (stages.get() != null) {
            stages.get().putAddressToMap(address);
        }

        return this;
    }

    public LinkedHashSet<Address> getAddresses() {
        return addresses;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Stage{");
        sb.append("name='").append(name).append('\'');
        if (description != null)
            sb.append(", description='").append(description).append('\'');
        sb.append(", environments=").append(addresses);
        sb.append('}');
        return sb.toString();
    }

    public Address findRemoteEnvironment() {
        return Iterables.find(addresses, Predicates.instanceOf(SshAddress.class));

    }

    public Collection<? extends Address> getHostsForRoles(List<String> stringRoles) {
        List<Role> roles;

        try {
            roles = newArrayList(transform(stringRoles, forMap(stages.get().rolenameToRole)));
        } catch (IllegalArgumentException e) {
            throw new StagesException("role not found: " + e.getMessage() + " on stage '" + name + "'");
        }

        LinkedHashSet<Address> hashSet = null;


        hashSet = Sets.newLinkedHashSet(
            concat(transform(roles,
                forMap(stages.get().roleToAddresses.asMap()))));


        return Lists.newArrayList(Sets.intersection(hashSet, addresses));
    }

    public List<String> validate(List<String> hosts) {
        List<Address> providedAddresses = hostsToAddresses(hosts);

        _validate(providedAddresses);

        return hosts;
    }

    public static final class StagesException extends RuntimeException {
        public StagesException(String message) {
            super(message);
        }
    }

    public List<Address> mapNamesToAddresses(List<String> hosts) {
        List<Address> providedAddresses = hostsToAddresses(hosts);

        _validate(providedAddresses);

        return newArrayList(providedAddresses);
    }

    private List<Address> hostsToAddresses(List<String> hosts) {
        return transform(hosts, forMap(stages.get().hostToAddress));
    }

    private void _validate(List<Address> providedAddresses) {
        try {
            if (!addresses.containsAll(providedAddresses)) {
                Sets.SetView<Address> missingHosts = Sets.difference(Sets.newHashSet(providedAddresses), addresses);

                throw new StagesException("hosts doesn't exist on stage '" + name + "': " +
                    transform(missingHosts, Functions2.method("getName")));
            }
        } catch (IllegalArgumentException e) {
            throw new StagesException("host doesn't exist on any stage: " +
                e.getMessage());
        }
    }

    public Stage addHosts(Iterable<Address> hosts) {
        for (Address host : hosts) {
            add(host);
        }

        return this;
    }

    public Stages getStages() {
        return stages.get();
    }
}
