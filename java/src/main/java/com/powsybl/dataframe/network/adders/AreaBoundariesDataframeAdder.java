/**
 * Copyright (c) 2024, Coreso SA (https://www.coreso.eu/) and TSCNET Services GmbH (https://www.tscnet.eu/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.dataframe.network.adders;

import com.powsybl.commons.PowsyblException;
import com.powsybl.dataframe.SeriesMetadata;
import com.powsybl.dataframe.update.IntSeries;
import com.powsybl.dataframe.update.StringSeries;
import com.powsybl.dataframe.update.UpdatingDataframe;
import com.powsybl.iidm.network.*;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

import static com.powsybl.dataframe.network.adders.SeriesUtils.getRequiredInts;
import static com.powsybl.dataframe.network.adders.SeriesUtils.getRequiredStrings;

/**
 * @author Damien Jeandemange <damien.jeandemange@artelys.com>
 */
public class AreaBoundariesDataframeAdder implements NetworkElementAdder {

    private static final List<SeriesMetadata> METADATA = List.of(
            SeriesMetadata.stringIndex("id"),
            SeriesMetadata.strings("element"),
            SeriesMetadata.strings("side"),
            SeriesMetadata.booleans("ac")
    );

    @Override
    public List<List<SeriesMetadata>> getMetadata() {
        return Collections.singletonList(METADATA);
    }

    private static final class AreaBoundaries {

        private final StringSeries ids;
        private final StringSeries elements;
        private final StringSeries sides;
        private final IntSeries acs;

        AreaBoundaries(UpdatingDataframe dataframe) {
            this.ids = getRequiredStrings(dataframe, "id");
            this.elements = getRequiredStrings(dataframe, "element");
            this.sides = dataframe.getStrings("side");
            this.acs = getRequiredInts(dataframe, "ac");
        }

        public StringSeries getIds() {
            return ids;
        }

        public StringSeries getElements() {
            return elements;
        }

        public StringSeries getSides() {
            return sides;
        }

        public IntSeries getAcs() {
            return acs;
        }
    }

    @Override
    public void addElements(Network network, List<UpdatingDataframe> dataframes) {
        UpdatingDataframe primaryTable = dataframes.get(0);
        AreaBoundaries series = new AreaBoundaries(primaryTable);

        Map<Area, List<Pair<DanglingLine, Boolean>>> danglingLineBoundaries = new HashMap<>();
        Map<Area, List<Pair<Terminal, Boolean>>> terminalBoundaries = new HashMap<>();
        for (int i = 0; i < primaryTable.getRowCount(); i++) {
            String areaId = series.getIds().get(i);
            String element = series.getElements().get(i);
            String side = series.getSides() == null ? "" : series.getSides().get(i);
            boolean ac = series.getAcs().get(i) == 1;
            Area area = network.getArea(areaId);
            if (area == null) {
                throw new PowsyblException("Area " + areaId + " not found");
            }
            // an empty element alone for an area indicates remove all boundaries in area
            Connectable<?> connectable = element.isEmpty() ? null : network.getConnectable(element);
            if (!element.isEmpty() && connectable == null) {
                throw new PowsyblException("Connectable " + element + " not found");
            }
            if (connectable == null) {
                danglingLineBoundaries.computeIfAbsent(area, k -> new ArrayList<>()).add(Pair.of(null, null));
                terminalBoundaries.computeIfAbsent(area, k -> new ArrayList<>()).add(Pair.of(null, null));
            } else if (connectable instanceof DanglingLine danglingLine) {
                danglingLineBoundaries.computeIfAbsent(area, k -> new ArrayList<>()).add(Pair.of(danglingLine, ac));
            } else if (connectable instanceof Injection<?> injection) {
                terminalBoundaries.computeIfAbsent(area, k -> new ArrayList<>()).add(Pair.of(injection.getTerminal(), ac));
            } else if (connectable instanceof Branch<?> branch) {
                terminalBoundaries.computeIfAbsent(area, k -> new ArrayList<>()).add(Pair.of(branch.getTerminal(TwoSides.valueOf(side)), ac));
            } else if (connectable instanceof ThreeWindingsTransformer t3wt) {
                terminalBoundaries.computeIfAbsent(area, k -> new ArrayList<>()).add(Pair.of(t3wt.getTerminal(ThreeSides.valueOf(side)), ac));
            }
        }
        // delete boundaries of involved areas
        Set<Area> areas = new HashSet<>(danglingLineBoundaries.keySet());
        areas.addAll(terminalBoundaries.keySet());
        areas.forEach(a -> {
            a.getAreaBoundaryStream().toList().forEach(areaBoundary -> {
                areaBoundary.getBoundary().ifPresent(a::removeAreaBoundary);
                areaBoundary.getTerminal().ifPresent(a::removeAreaBoundary);
            });
        });
        // create new boundaries
        danglingLineBoundaries.forEach((area, list) -> {
            list.stream()
                    .filter(pair -> !(pair.getLeft() == null))
                    .forEach(pair -> area.newAreaBoundary()
                    .setBoundary(pair.getLeft().getBoundary())
                    .setAc(pair.getRight())
                    .add());
        });
        terminalBoundaries.forEach((area, list) -> {
            list.stream()
                    .filter(pair -> !(pair.getLeft() == null))
                    .forEach(pair -> area.newAreaBoundary()
                    .setTerminal(pair.getLeft())
                    .setAc(pair.getRight())
                    .add());
        });
    }
}
