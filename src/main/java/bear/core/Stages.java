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

import bear.session.Address;
import bear.session.SshAddress;
import chaschev.lang.Predicates2;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

import java.util.*;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Stages {
    final List<Stage> stages = new ArrayList<Stage>();

    final Multimap<Role, Address> roleToAddresses = LinkedHashMultimap.create();
    final Map<String, Role> rolenameToRole = new LinkedHashMap<String, Role>();


    final Map<String, Address> hostToAddress = new LinkedHashMap<String, Address>();
    public final GlobalContext global;

    public Stages(GlobalContext global) {
        this.global = global;
    }

    public int size() {
        return stages.size();
    }

    public Stages add(Stage stage) {
        stages.add(stage);
        stage.addToStages(this);
        return this;
    }

    public List<Stage> getStages() {
        return stages;
    }

    public Stages assignRoleToHosts(Role role, String csvHosts){
        for (String host : splitCSV(csvHosts)) {
            assignRoleToAddress(role, getHost(host));
        }


        return this;
    }

    public Stages assignRoleToStage(Role role, String stageName){
        return assignRoleToStage(findByName(stageName), role);
    }

    public Stage findByName(String stageName) {
        return Iterables.find(stages, Predicates2.fieldEquals("name", stageName));
    }

    public Stages assignRoleToStage(Stage stage, Role role){
        for (Address address : stage.getAddresses()) {
            assignRoleToAddress(role, address);
        }

        return this;
    }

    private void assignRoleToAddress(Role role, Address address) {
        roleToAddresses.put(role, address);
        rolenameToRole.put(role.role, role);

        address.getRoles().add(role);
    }


    Address putAddressToMap(Address address) {
        return hostToAddress.put(address.getName(), address);
    }

    public Set<Role> getRoles(String host) {
        return hostToAddress.get(host).getRoles();
    }

    public Iterable<Address> hosts(String csvNames){
        return Iterables.transform(splitCSV(csvNames), new Function<String, Address>() {
            @Override
            public Address apply(String hostname) {
                return getHost(hostname);
            }
        });
    }

    private static Iterable<String> splitCSV(String csvNames) {
        return Splitter.on(',').trimResults().split(csvNames);
    }

    public Address getHost(String hostname) {
        Address address = hostToAddress.get(hostname);

        if(address == null){
            hostToAddress.put(hostname, address = new SshAddress(hostname, null, null, hostname));
        }

        return address;
    }
}

