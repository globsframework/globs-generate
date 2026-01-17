package org.globsframework.model.generator;

import org.globsframework.core.metamodel.fields.*;

public class FieldVisitorToVisitName implements org.globsframework.core.metamodel.fields.FieldVisitor {
    public String name;
    public boolean isArray;
    SpecificName characteristic;

    public void setCharacteristic(SpecificName characteristic) {
        this.characteristic = characteristic;
    }

    public FieldVisitorToVisitName withFieldType() {
        setCharacteristic(SpecificName.fieldType);
        return this;
    }

    public FieldVisitorToVisitName withOutputType() {
        setCharacteristic(SpecificName.outputType);
        return this;
    }

    public FieldVisitorToVisitName withSimpleUserType() {
        setCharacteristic(SpecificName.outputTypeSimple);
        return this;
    }

    public FieldVisitorToVisitName withMethodVisitor() {
        setCharacteristic(SpecificName.visitor);
        return this;
    }

    public FieldVisitorToVisitName withAbstractGetAccessor() {
        setCharacteristic(SpecificName.getAccessor);
        return this;
    }

    public FieldVisitorToVisitName withAbstractSetAccessor() {
        setCharacteristic(SpecificName.setAccessor);
        return this;
    }

    public FieldVisitorToVisitName withWithNativeType() {
        setCharacteristic(SpecificName.nativeType);
        return this;
    }

    public void visitInteger(IntegerField field) {
        isArray = false;
        name = switch (characteristic) {
            case visitor -> "visitInteger";
            case fieldType -> "IntegerField";
            case outputTypeSimple -> "java/lang/Integer";
            case outputType -> "Ljava/lang/Integer;";
            case nativeType -> "I";
            case getAccessor -> "AbstractGlobGetIntAccessor";
            case setAccessor -> "AbstractGlobSetIntAccessor";
        };
    }

    public void visitIntegerArray(IntegerArrayField field) {
        isArray = true;
        name = switch (characteristic) {
            case visitor -> "visitIntegerArray";
            case fieldType -> "IntegerArrayField";
            case outputTypeSimple, nativeType, outputType -> "[I";
            case getAccessor -> "AbstractGlobGetIntArrayAccessor";
            case setAccessor -> "AbstractGlobSetIntArrayAccessor";
        };
    }

    public void visitDouble(DoubleField field) {
        isArray = false;
        name = switch (characteristic) {
            case visitor -> "visitDouble";
            case fieldType -> "DoubleField";
            case outputTypeSimple -> "java/lang/Double";
            case outputType -> "Ljava/lang/Double;";
            case nativeType -> "D";
            case getAccessor -> "AbstractGlobGetDoubleAccessor";
            case setAccessor -> "AbstractGlobSetDoubleAccessor";
        };
    }

    public void visitBoolean(BooleanField field) {
        isArray = false;
        name = switch (characteristic) {
            case visitor -> "visitBoolean";
            case fieldType -> "BooleanField";
            case outputType -> "Ljava/lang/Boolean;";
            case nativeType -> "Z";
            case outputTypeSimple -> "java/lang/Boolean";
            case getAccessor -> "AbstractGlobGetBooleanAccessor";
            case setAccessor -> "AbstractGlobSetBooleanAccessor";
        };
    }


    public void visitDoubleArray(DoubleArrayField field) {
        isArray = true;
        name = switch (characteristic) {
            case visitor -> "visitDoubleArray";
            case fieldType -> "DoubleArrayField";
            case outputTypeSimple, outputType, nativeType -> "[D";
            case getAccessor -> "AbstractGlobGetDoubleArrayAccessor";
            case setAccessor -> "AbstractGlobSetDoubleArrayAccessor";
        };
    }

    public void visitBigDecimal(BigDecimalField field) {
        isArray = false;
        name = switch (characteristic) {
            case visitor -> "visitBigDecimal";
            case fieldType -> "BigDecimalField";
            case outputTypeSimple -> "java/math/BigDecimal";
            case outputType, nativeType -> "Ljava/math/BigDecimal;";
            case getAccessor -> "AbstractGlobGetBigDecimalArrayAccessor";
            case setAccessor -> "AbstractGlobSetBigDecimalArrayAccessor";
        };
    }

    public void visitBigDecimalArray(BigDecimalArrayField field) {
        isArray = true;
        name = switch (characteristic) {
            case visitor -> "visitBigDecimalArray";
            case fieldType -> "BigDecimalArrayField";
            case outputTypeSimple, outputType, nativeType -> "[Ljava/math/BigDecimal;";
            case getAccessor -> "AbstractGlobGetBigDecimalArrayAccessor";
            case setAccessor -> "AbstractGlobSetBigDecimalArrayAccessor";
        };
    }

    public void visitString(StringField field) {
        isArray = false;
        name = switch (characteristic) {
            case visitor -> "visitString";
            case fieldType -> "StringField";
            case outputTypeSimple -> "java/lang/String";
            case outputType, nativeType -> "Ljava/lang/String;";
            case getAccessor -> "AbstractGlobGetStringAccessor";
            case setAccessor -> "AbstractGlobSetStringAccessor";
        };
    }

    public void visitStringArray(StringArrayField field) {
        isArray = true;
        name = switch (characteristic) {
            case visitor -> "visitStringArray";
            case fieldType -> "StringArrayField";
            case outputTypeSimple, nativeType, outputType -> "[Ljava/lang/String;";
            case getAccessor -> "AbstractGlobGetStringArrayAccessor";
            case setAccessor -> "AbstractGlobSetStringArrayAccessor";
        };
    }

    public void visitBooleanArray(BooleanArrayField field) {
        isArray = true;
        name = switch (characteristic) {
            case visitor -> "visitBooleanArray";
            case fieldType -> "BooleanArrayField";
            case outputTypeSimple, nativeType, outputType -> "[Z";
            case getAccessor -> "AbstractGlobGetBooleanArrayAccessor";
            case setAccessor -> "AbstractGlobSetBooleanArrayAccessor";
        };
    }

    public void visitLong(LongField field) {
        isArray = false;
        name = switch (characteristic) {
            case visitor -> "visitLong";
            case fieldType -> "LongField";
            case outputTypeSimple -> "java/lang/Long";
            case outputType -> "Ljava/lang/Long;";
            case nativeType -> "J";
            case getAccessor -> "AbstractGlobGetLongAccessor";
            case setAccessor -> "AbstractGlobSetLongAccessor";
        };
    }

    public void visitLongArray(LongArrayField field) {
        isArray = true;
        name = switch (characteristic) {
            case visitor -> "visitLongArray";
            case fieldType -> "LongArrayField";
            case outputTypeSimple, outputType, nativeType -> "[J";
            case getAccessor -> "AbstractGlobGetLongArrayAccessor";
            case setAccessor -> "AbstractGlobSetLongArrayAccessor";
        };
    }

    public void visitDate(DateField field) {
        isArray = false;
        name = switch (characteristic) {
            case visitor -> "visitDate";
            case fieldType -> "DateField";
            case outputTypeSimple -> "java/time/LocalDate";
            case nativeType, outputType -> "Ljava/time/LocalDate;";
            case getAccessor -> "AbstractGlobGetDateAccessor";
            case setAccessor -> "AbstractGlobSetDateAccessor";
        };
    }

    public void visitDateTime(DateTimeField field) {
        isArray = false;
        name = switch (characteristic) {
            case visitor -> "visitDateTime";
            case fieldType -> "DateTimeField";
            case outputTypeSimple -> "java/time/ZonedDateTime";
            case nativeType, outputType -> "Ljava/time/ZonedDateTime;";
            case getAccessor -> "AbstractGlobGetDateTimeAccessor";
            case setAccessor -> "AbstractGlobSetDateTimeAccessor";
        };
    }

    public void visitBytes(BytesField field) {
        isArray = false;
        name = switch (characteristic) {
            case visitor -> "visitBlob";
            case fieldType -> "BlobField";
            case outputTypeSimple, nativeType, outputType -> "[B";
            case getAccessor -> "AbstractGlobGetBytesAccessor";
            case setAccessor -> "AbstractGlobSetBytesAccessor";
        };
    }

    public void visitGlob(GlobField field) {
        isArray = false;
        name = switch (characteristic) {
            case visitor -> "visitGlob";
            case fieldType -> "GlobField";
            case outputTypeSimple -> "org/globsframework/core/model/Glob";
            case nativeType, outputType -> "Lorg/globsframework/core/model/Glob;";
            case getAccessor -> "AbstractGlobGetGlobAccessor";
            case setAccessor -> "AbstractGlobSetGlobAccessor";
        };
    }

    public void visitGlobArray(GlobArrayField field) {
        isArray = true;
        name = switch (characteristic) {
            case visitor -> "visitGlobArray";
            case fieldType -> "GlobArrayField";
            case outputTypeSimple, nativeType, outputType -> "[Lorg/globsframework/core/model/Glob;";
            case getAccessor -> "AbstractGlobGetGlobArrayAccessor";
            case setAccessor -> "AbstractGlobSetGlobArrayAccessor";
        };
    }

    public void visitUnionGlob(GlobUnionField field) {
        isArray = false;
        name = switch (characteristic) {
            case visitor -> "visitUnionGlob";
            case fieldType -> "GlobUnionField";
            case outputTypeSimple -> "org/globsframework/core/model/Glob";
            case nativeType, outputType -> "Lorg/globsframework/core/model/Glob;";
            case getAccessor -> "AbstractGlobGetGlobUnionAccessor";
            case setAccessor -> "AbstractGlobSetGlobUnionAccessor";
        };
    }

    public void visitUnionGlobArray(GlobArrayUnionField field) {
        isArray = true;
        name = switch (characteristic) {
            case visitor -> "visitUnionGlobArray";
            case fieldType -> "GlobArrayUnionField";
            case outputTypeSimple, nativeType, outputType -> "[Lorg/globsframework/core/model/Glob;";
            case getAccessor -> "AbstractGlobGetGlobUnionArrayAccessor";
            case setAccessor -> "AbstractGlobSetGlobUnionArrayAccessor";
        };
    }
}
