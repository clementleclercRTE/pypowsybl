/**
 * Copyright (c) 2024, RTE (http://www.rte-france.com/)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * SPDX-License-Identifier: MPL-2.0
 */
package com.powsybl.dataframe.dynamic.adders;

import com.powsybl.dataframe.update.DefaultUpdatingDataframe;
import com.powsybl.dataframe.update.TestStringSeries;
import com.powsybl.iidm.network.Network;
import com.powsybl.iidm.network.TwoSides;
import com.powsybl.iidm.network.test.EurostagTutorialExample1Factory;
import com.powsybl.iidm.network.test.HvdcTestNetwork;
import com.powsybl.iidm.network.test.SvcTestCaseFactory;
import com.powsybl.python.commons.PyPowsyblApiHeader;
import com.powsybl.python.dynamic.PythonDynamicModelsSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.powsybl.dataframe.dynamic.adders.DynamicModelDataframeConstants.*;
import static com.powsybl.python.commons.PyPowsyblApiHeader.DynamicMappingType.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Laurent Issertial {@literal <laurent.issertial at rte-france.com>}
 */
//TODO test empty dataframe and wrong lib
public class DynamicModelsAdderTest {

    private DefaultUpdatingDataframe dataframe;
    private PythonDynamicModelsSupplier dynamicModelsSupplier;

    @BeforeEach
    void setup() {
        dataframe = new DefaultUpdatingDataframe(2);
        dynamicModelsSupplier = new PythonDynamicModelsSupplier();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("hvdcDataProvider")
    void testHvdcAdders(PyPowsyblApiHeader.DynamicMappingType mappingType, String modelName, boolean dangling) {

        String staticId = "L";
        Network network = HvdcTestNetwork.createVsc();
        // TODO try 2 rows dataframe
        dataframe = new DefaultUpdatingDataframe(1);
        dataframe.addSeries(STATIC_ID, true, new TestStringSeries(staticId, staticId));
        dataframe.addSeries(PARAMETER_SET_ID, false, new TestStringSeries("hvdc_par", "hvdc_par"));
        dataframe.addSeries(MODEL_NAME, false, new TestStringSeries(modelName, ""));
        if (dangling) {
            dataframe.addSeries(DANGLING_SIDE, false, new TestStringSeries(String.valueOf(TwoSides.TWO)));
        }
        DynamicMappingAdderFactory.getAdder(mappingType).addElements(dynamicModelsSupplier, dataframe);

        assertThat(dynamicModelsSupplier.get(network)).satisfiesExactly(
                model1 -> assertThat(model1).hasFieldOrPropertyWithValue("dynamicModelId", staticId)
                        .hasFieldOrPropertyWithValue("lib", modelName));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("equipmentDataProvider")
    void testEquipmentsAdder(PyPowsyblApiHeader.DynamicMappingType mappingType, String modelName, Network network, String staticId) {

        dataframe.addSeries(STATIC_ID, true, new TestStringSeries(staticId, staticId));
        dataframe.addSeries(PARAMETER_SET_ID, false, new TestStringSeries("eq_par", "eq_par"));
        dataframe.addSeries(MODEL_NAME, false, new TestStringSeries(modelName, ""));
        DynamicMappingAdderFactory.getAdder(mappingType).addElements(dynamicModelsSupplier, dataframe);

        assertThat(dynamicModelsSupplier.get(network)).satisfiesExactly(
                model1 -> assertThat(model1).hasFieldOrPropertyWithValue("dynamicModelId", staticId)
                        .hasFieldOrPropertyWithValue("lib", modelName),
                model2 -> assertThat(model2).hasFieldOrPropertyWithValue("dynamicModelId", staticId));
    }

    @Test
    void testIncompleteDataFrame() {
        Network network = EurostagTutorialExample1Factory.create();
        DefaultUpdatingDataframe missingStaticDF = new DefaultUpdatingDataframe(1);
        missingStaticDF.addSeries(PARAMETER_SET_ID, false, new TestStringSeries("eq_par"));
        DynamicMappingAdderFactory.getAdder(BASE_LOAD).addElements(dynamicModelsSupplier, missingStaticDF);
        DefaultUpdatingDataframe missingParamDF = new DefaultUpdatingDataframe(1);
        missingParamDF.addSeries(STATIC_ID, false, new TestStringSeries("LOAD"));
        DynamicMappingAdderFactory.getAdder(BASE_LOAD).addElements(dynamicModelsSupplier, missingParamDF);
        assertThat(dynamicModelsSupplier.get(network)).isEmpty();
    }

    static Stream<Arguments> hvdcDataProvider() {
        return Stream.of(
                Arguments.of(HVDC_P, "HvdcPVDangling", true),
                Arguments.of(HVDC_VSC, "HvdcVSC", false)
        );
    }

    static Stream<Arguments> equipmentDataProvider() {
        return Stream.of(
                Arguments.of(BASE_LOAD, "LoadPQ", EurostagTutorialExample1Factory.create(), "LOAD"),
                Arguments.of(LOAD_ONE_TRANSFORMER, "LoadOneTransformer", EurostagTutorialExample1Factory.create(), "LOAD"),
                Arguments.of(LOAD_ONE_TRANSFORMER_TAP_CHANGER, "LoadOneTransformerTapChanger", EurostagTutorialExample1Factory.create(), "LOAD"),
                Arguments.of(LOAD_TWO_TRANSFORMERS, "LoadTwoTransformers", EurostagTutorialExample1Factory.create(), "LOAD"),
                Arguments.of(LOAD_TWO_TRANSFORMERS_TAP_CHANGERS, "LoadTwoTransformersTapChangers", EurostagTutorialExample1Factory.create(), "LOAD"),
                Arguments.of(BASE_GENERATOR, "GeneratorFictitious", EurostagTutorialExample1Factory.create(), "GEN"),
                Arguments.of(SYNCHRONIZED_GENERATOR, "GeneratorPVFixed", EurostagTutorialExample1Factory.create(), "GEN"),
                Arguments.of(SYNCHRONOUS_GENERATOR, "GeneratorSynchronousThreeWindings", EurostagTutorialExample1Factory.create(), "GEN"),
                Arguments.of(WECC, "WT4BWeccCurrentSource", EurostagTutorialExample1Factory.create(), "GEN"),
                Arguments.of(GRID_FORMING_CONVERTER, "GridFormingConverterMatchingControl", EurostagTutorialExample1Factory.create(), "GEN"),
                Arguments.of(TRANSFORMER, "TransformerFixedRatio", EurostagTutorialExample1Factory.create(), "NGEN_NHV1"),
                Arguments.of(BASE_STATIC_VAR_COMPENSATOR, "StaticVarCompensatorPV", SvcTestCaseFactory.create(), "SVC2"),
                Arguments.of(BASE_LINE, "Line", EurostagTutorialExample1Factory.create(), "NHV1_NHV2_1"),
                Arguments.of(BASE_BUS, "Bus", EurostagTutorialExample1Factory.create(), "NHV1"),
                Arguments.of(INFINITE_BUS, "InfiniteBus", EurostagTutorialExample1Factory.create(), "NHV1")
        );
    }
}
