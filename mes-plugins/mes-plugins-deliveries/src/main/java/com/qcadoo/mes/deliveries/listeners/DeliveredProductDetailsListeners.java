/**
 * ***************************************************************************
 * Copyright (c) 2010 Qcadoo Limited
 * Project: Qcadoo MES
 * Version: 1.4
 *
 * This file is part of Qcadoo.
 *
 * Qcadoo is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation; either version 3 of the License,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 * ***************************************************************************
 */
package com.qcadoo.mes.deliveries.listeners;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.qcadoo.mes.basic.CalculationQuantityService;
import com.qcadoo.mes.basic.constants.ProductFields;
import com.qcadoo.mes.deliveries.DeliveriesService;
import com.qcadoo.mes.deliveries.constants.DeliveredProductFields;
import com.qcadoo.mes.deliveries.hooks.DeliveredProductDetailsHooks;
import com.qcadoo.model.api.Entity;
import com.qcadoo.model.api.NumberService;
import com.qcadoo.view.api.ComponentState;
import com.qcadoo.view.api.ComponentState.MessageType;
import com.qcadoo.view.api.ViewDefinitionState;
import com.qcadoo.view.api.components.FieldComponent;
import com.qcadoo.view.api.components.FormComponent;
import com.qcadoo.view.api.components.LookupComponent;
import com.qcadoo.view.api.components.lookup.FilterValueHolder;

@Service
public class DeliveredProductDetailsListeners {

    private static final String L_FORM = "form";

    @Autowired
    private NumberService numberService;

    @Autowired
    private CalculationQuantityService calculationQuantityService;

    @Autowired
    private DeliveriesService deliveriesService;

    @Autowired
    private DeliveredProductDetailsHooks deliveredProductDetailsHooks;

    public void onSelectedEntityChange(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        LookupComponent productLookup = (LookupComponent) view.getComponentByReference(DeliveredProductFields.PRODUCT);
        Entity product = productLookup.getEntity();

        LookupComponent additionalCodeLookup = (LookupComponent) view
                .getComponentByReference(DeliveredProductFields.ADDITIONAL_CODE);
        LookupComponent storageLocationLookup = (LookupComponent) view
                .getComponentByReference(DeliveredProductFields.STORAGE_LOCATION);

        if (Objects.nonNull(product)) {
            filterByProduct(additionalCodeLookup, product.getId());
            filterByProduct(storageLocationLookup, product.getId());
        } else {
            clearAndDisable(additionalCodeLookup);
            clearAndDisable(storageLocationLookup);
        }

        fillConversion(view, state, args);
        quantityChange(view, state, args);
    }

    private void filterByProduct(final LookupComponent lookupComponent, final Long id) {
        lookupComponent.setFieldValue(null);
        lookupComponent.setEnabled(true);

        FilterValueHolder filterValueHolder = lookupComponent.getFilterValue();
        filterValueHolder.put(DeliveredProductFields.PRODUCT, id);

        lookupComponent.setFilterValue(filterValueHolder);
        lookupComponent.requestComponentUpdateState();
    }

    private void clearAndDisable(final LookupComponent lookupComponent) {
        lookupComponent.setFieldValue(null);
        lookupComponent.setEnabled(false);
        lookupComponent.requestComponentUpdateState();
    }

    public void fillUnitFields(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        deliveredProductDetailsHooks.fillUnitFields(view);
    }

    public void fillCurrencyFields(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        deliveredProductDetailsHooks.fillCurrencyFields(view);
    }

    public void fillOrderedQuantities(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        deliveredProductDetailsHooks.fillOrderedQuantities(view);
    }

    public void fillConversion(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        deliveredProductDetailsHooks.fillConversion(view);
    }

    public void calculatePriceFromTotalPrice(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        deliveriesService.recalculatePriceFromTotalPrice(view, DeliveredProductFields.DELIVERED_QUANTITY);
    }

    public void calculatePriceFromPricePerUnit(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        deliveriesService.recalculatePriceFromPricePerUnit(view, DeliveredProductFields.DELIVERED_QUANTITY);
    }

    public void calculatePrice(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        deliveriesService.recalculatePrice(view, DeliveredProductFields.DELIVERED_QUANTITY);
    }

    public void quantityChange(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent deliveredProductForm = (FormComponent) view.getComponentByReference(L_FORM);
        Entity deliveredProduct = deliveredProductForm.getEntity();
        Entity product = deliveredProduct.getBelongsToField(DeliveredProductFields.PRODUCT);

        if (decimalFieldsInvalid(deliveredProductForm) || Objects.isNull(product)) {
            return;
        }

        BigDecimal conversion = deliveredProduct.getDecimalField(DeliveredProductFields.CONVERSION);
        BigDecimal deliveredQuantity = deliveredProduct.getDecimalField(DeliveredProductFields.DELIVERED_QUANTITY);

        if (Objects.nonNull(conversion) && Objects.nonNull(deliveredQuantity)) {
            String additionalQuantityUnit = Optional.ofNullable(product.getStringField(ProductFields.ADDITIONAL_UNIT))
                    .orElse(product.getStringField(ProductFields.UNIT));

            BigDecimal newAdditionalQuantity = calculationQuantityService.calculateAdditionalQuantity(deliveredQuantity,
                    conversion, additionalQuantityUnit);

            FieldComponent additionalQuantityField = (FieldComponent) view
                    .getComponentByReference(DeliveredProductFields.ADDITIONAL_QUANTITY);

            additionalQuantityField.setFieldValue(numberService.formatWithMinimumFractionDigits(newAdditionalQuantity, 0));
            additionalQuantityField.requestComponentUpdateState();
        }
    }

    private boolean decimalFieldsInvalid(final FormComponent formComponent) {
        String[] fieldNames = { DeliveredProductFields.ADDITIONAL_QUANTITY, DeliveredProductFields.CONVERSION,
                DeliveredProductFields.DELIVERED_QUANTITY };

        boolean valid = false;

        Entity entity = formComponent.getEntity();

        for (String fieldName : fieldNames) {
            try {
                entity.getDecimalField(fieldName);
            } catch (IllegalArgumentException e) {
                formComponent.findFieldComponentByName(fieldName)
                        .addMessage("qcadooView.validate.field.error.invalidNumericFormat", MessageType.FAILURE);

                valid = true;
            }
        }

        return valid;
    }

    public void additionalQuantityChange(final ViewDefinitionState view, final ComponentState state, final String[] args) {
        FormComponent deliveredProductForm = (FormComponent) view.getComponentByReference(L_FORM);
        Entity deliveredProduct = deliveredProductForm.getEntity();
        Entity product = deliveredProduct.getBelongsToField(DeliveredProductFields.PRODUCT);

        if (decimalFieldsInvalid(deliveredProductForm) || Objects.isNull(product)) {
            return;
        }

        BigDecimal conversion = deliveredProduct.getDecimalField(DeliveredProductFields.CONVERSION);
        BigDecimal additionalQuantity = deliveredProduct.getDecimalField(DeliveredProductFields.ADDITIONAL_QUANTITY);

        if (Objects.nonNull(conversion) && Objects.nonNull(additionalQuantity)) {
            String deliveredQuantityUnit = product.getStringField(ProductFields.UNIT);

            BigDecimal newDeliveredQuantity = calculationQuantityService.calculateQuantity(additionalQuantity, conversion,
                    deliveredQuantityUnit);

            FieldComponent deliveredQuantityField = (FieldComponent) view
                    .getComponentByReference(DeliveredProductFields.DELIVERED_QUANTITY);

            deliveredQuantityField.setFieldValue(numberService.formatWithMinimumFractionDigits(newDeliveredQuantity, 0));
            deliveredQuantityField.requestComponentUpdateState();
        }
    }

    public void setBatchLookupProductFilterValue(final ViewDefinitionState view, final ComponentState state,
            final String[] args) {
        FormComponent deliveredProductForm = (FormComponent) view.getComponentByReference(L_FORM);

        Entity deliveredProduct = deliveredProductForm.getPersistedEntityWithIncludedFormValues();

        deliveredProductDetailsHooks.setBatchLookupProductFilterValue(view, deliveredProduct);
    }

}
