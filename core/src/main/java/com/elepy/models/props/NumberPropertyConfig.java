package com.elepy.models.props;

import com.elepy.annotations.Number;
import com.elepy.models.FieldType;
import com.elepy.models.NumberType;
import com.elepy.models.Property;
import com.elepy.utils.ReflectionUtils;

import java.lang.reflect.AccessibleObject;

public class NumberPropertyConfig implements PropertyConfig {
    private final float minimum;
    private final float maximum;
    private final NumberType numberType;

    public NumberPropertyConfig(float minimum, float maximum, NumberType numberType) {
        this.minimum = minimum;
        this.maximum = maximum;
        this.numberType = numberType;
    }

    public static NumberPropertyConfig of(AccessibleObject field) {
        return of(field, ReflectionUtils.returnTypeOf(field));
    }

    //This method  was made to get number configuration from arrays by passing the actual  generic class type
    public static NumberPropertyConfig of(AccessibleObject field, Class<?> actualNumberType) {
        final Number annotation = field.getAnnotation(Number.class);
        return new NumberPropertyConfig(
                annotation == null ? Integer.MIN_VALUE : annotation.minimum(),
                annotation == null ? Integer.MAX_VALUE : annotation.maximum(),
                NumberType.guessType(actualNumberType)
        );
    }

    public static NumberPropertyConfig of(Property property) {
        return new NumberPropertyConfig(
                property.getExtra("minimum"),
                property.getExtra("maximum"),
                property.getExtra("numberType")
        );
    }

    @Override
    public void config(Property property) {
        property.setType(FieldType.NUMBER);
        property.setExtra("minimum", minimum);
        property.setExtra("maximum", maximum);
        property.setExtra("numberType", numberType);
    }

    public float getMinimum() {
        return minimum;
    }

    public float getMaximum() {
        return maximum;
    }
}
