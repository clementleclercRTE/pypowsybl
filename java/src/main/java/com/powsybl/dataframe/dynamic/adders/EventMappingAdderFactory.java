/**
 * Copyright (c) 2020-2023, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.dataframe.dynamic.adders;

import com.powsybl.python.commons.PyPowsyblApiHeader.EventMappingType;

import java.util.Map;

/**
 * @author Laurent Issertial {@literal <laurent.issertial at rte-france.com>}
 */
public final class EventMappingAdderFactory {

    private static final Map<EventMappingType, EventMappingAdder> ADDERS = Map.ofEntries(
            Map.entry(EventMappingType.DISCONNECT, new DisconnectAdder()),
            Map.entry(EventMappingType.NODE_FAULT, new NodeFaultAdder()),
            Map.entry(EventMappingType.ACTIVE_POWER_VARIATION, new ActivePowerVariationAdder())
    );

    public static EventMappingAdder getAdder(EventMappingType type) {
        return ADDERS.get(type);
    }

    private EventMappingAdderFactory() {
    }
}
