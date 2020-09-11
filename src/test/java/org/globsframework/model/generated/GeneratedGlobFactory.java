package org.globsframework.model.generated;

import org.globsframework.metamodel.Field;
import org.globsframework.metamodel.GlobType;
import org.globsframework.metamodel.fields.*;
import org.globsframework.model.FieldValues;
import org.globsframework.model.MutableGlob;
import org.globsframework.model.generator.AbstractGeneratedGlobFactory;
import org.globsframework.model.generator.AsmGlobGenerator;
import org.globsframework.model.globaccessor.get.GlobGetAccessor;
import org.globsframework.model.globaccessor.set.GlobSetAccessor;

import java.io.InputStream;

public class GeneratedGlobFactory extends AbstractGeneratedGlobFactory {
    public final static GlobType GLOB_TYPE;
    public final static IntegerField f1;
    public final static StringField f2;
    public final static DoubleField f3;
    public final static IntegerArrayField f4;
    public final static StringField f5;

    static {
        GLOB_TYPE = AsmGlobGenerator.TYPE;
        f1 = (IntegerField) GLOB_TYPE.getField("f1");
        f2 = (StringField) GLOB_TYPE.getField("f2");
        f3 = (DoubleField) GLOB_TYPE.getField("f3");
        f4 = (IntegerArrayField) GLOB_TYPE.getField("f4");
        f5 = (StringField) GLOB_TYPE.getField("f5");
    }

    public MutableGlob create() {
        return new GeneratedGlob();
    }


//    // l'appelant lit la taille et austela taille du buffer pour qu'on n'est plus de if sur la taille.
//    public MutableGlob read(GlobReader globReader) {
//        GeneratedGlob generatedGlob = new GeneratedGlob();
//        int nullFlag_1 = globReader.readInt();
//        if ((nullFlag_1 & 0x1) == 0) {
//            generatedGlob.i1 = globReader.readInt();
//        }
//        if ((nullFlag_1 & 0x2) == 0) {
//            generatedGlob.i3 = Double.longBitsToDouble(globReader.readLong());
//        }
//        generatedGlob.i2 = globReader.readString();
//
//        return generatedGlob;
//    }
//
//    public interface GlobReader {
//        void readHead(int size);
//
//        int readInt();
//
//        long readLong();
//
//        String readString();
//    }

    public <T extends FieldVisitor> T accept(T visitor) throws Exception {
        visitor.visitInteger(f1);
        visitor.visitString(f2);
        visitor.visitDouble(f3);
        visitor.visitIntegerArray(f4);
        return visitor;
    }

    public <T extends FieldVisitorWithContext<C>, C> T accept(T visitor, C context) throws Exception {
        visitor.visitInteger(f1, context);
        visitor.visitString(f2, context);
        visitor.visitDouble(f3, context);
        visitor.visitIntegerArray(f4, context);
        return visitor;
    }

    public <T extends FieldVisitorWithTwoContext<C, D>, C, D> T accept(T visitor, C ctx1, D ctx2) throws Exception{
        visitor.visitInteger(f1, ctx1, ctx2);
        visitor.visitString(f2, ctx1, ctx2);
        visitor.visitDouble(f3, ctx1, ctx2);
        visitor.visitIntegerArray(f4, ctx1, ctx2);
        return visitor;
    }

//    interface Walker {
//        boolean nextAndIsNull();
//
//        int readInt();
//
//        double readDouble();
//
//        String readString();
//
//        double[] readDoubleArray();
//
//
//    }

    /*
    write 1202.275904ms
write 1015.455029ms
write 896.514903ms
write 532.867158ms
write 537.414884ms
write 535.877893ms
write 545.015316ms
read 925.489889ms => 1080508.8330900178 objects/s
read 818.195399ms => 1222201.935163901 objects/s
read 780.236045ms => 1281663.4227658631 objects/s
read 784.836502ms => 1274150.7275103778 objects/s
read 787.447333ms => 1269926.200892981 objects/s
read 786.195929ms => 1271947.5681741924 objects/s
read 749.937654ms => 1333444.1798811185 objects/s
read 723.090492ms => 1382952.7715598838 objects/s


write 1135.726453ms
write 942.902398ms
write 946.2246ms
write 562.163082ms
write 531.629697ms
write 551.126999ms
write 577.774764ms
read 867.233872ms => 1153091.4927178952 objects/s
read 808.38525ms => 1237033.9513245695 objects/s
read 758.926295ms => 1317651.011156492 objects/s
read 746.407375ms => 1339750.9637414822 objects/s
read 739.03604ms => 1353113.9834533646 objects/s
read 799.881705ms => 1250184.863273001 objects/s
read 822.281287ms => 1216128.8549425544 objects/s
read 841.072838ms => 1188957.6678970102 objects/s


------ avec is set en premier.

write 1032.36276ms
write 1017.584913ms
write 1006.661792ms
write 576.736029ms
write 575.311708ms
write 585.07603ms
write 574.064319ms
read 889.707365ms => 1123965.069121351 objects/s
read 810.13072ms => 1234368.6954618879 objects/s
read 824.940301ms => 1212208.9304981113 objects/s
read 813.711186ms => 1228937.2657094086 objects/s
read 813.912326ms => 1228633.5616939655 objects/s
read 812.984117ms => 1230036.3304637598 objects/s
read 760.969471ms => 1314113.1649944996 objects/s
read 755.688731ms => 1323296.165441959 objects/s

write 1092.896494ms
write 968.450577ms
write 956.350893ms
write 591.756494ms
write 545.692293ms
write 537.976707ms
write 542.944705ms
read 820.124037ms => 1219327.7539553447 objects/s
read 738.372832ms => 1354329.353223007 objects/s
read 741.995931ms => 1347716.2855223257 objects/s
read 753.099286ms => 1327846.166620851 objects/s
read 736.527285ms => 1357722.9525176382 objects/s
read 742.249376ms => 1347256.1006235187 objects/s
read 741.039734ms => 1349455.304646323 objects/s
read 742.611146ms => 1346599.7721504709 objects/s

     */

    public static <T extends FieldValues.Functor>
    T processValue(GeneratedGlob generated, T visitor) throws Exception {
        // l'impact de perf du isSet est assez important : pb d'inline? cf PerfReadWriteTest en Json. et le gain de perf met plus de temps a arrivé.
        boolean setf1 = generated.isSet(f1);
        boolean isSetf2 = generated.isSet(f2);
        boolean isSetf3 = generated.isSet(f3);
        boolean isSetf4 = generated.isSet(f4);
        boolean isSetf5 = generated.isSet(f5);
        if (setf1) visitor.process(f1, generated.get_i1());
        if (isSetf2) visitor.process(f2, generated.i2);
        if (isSetf3) visitor.process(f3, generated.get_i3());
        if (isSetf4) visitor.process(f4, generated.ia1);
        if (isSetf5) visitor.process(f5, generated.get_name());
        return visitor;
    }

    public static <T extends FieldValueVisitor>
    T acceptValueStatic(GeneratedGlob generated, T visitor) throws Exception {
        boolean isSetf1 = generated.isSet(f1);
        boolean isSetf2 = generated.isSet(f2);
        boolean isSetf3 = generated.isSet(f3);
        boolean isSetf4 = generated.isSet(f4);
        if (isSetf1) visitor.visitInteger(f1, generated.get_i1() );
        if (isSetf2) visitor.visitString(f2, generated.i2);
        if (isSetf3) visitor.visitDouble(f3, generated.get_i3());
        if (isSetf4) visitor.visitIntegerArray(f4, generated.ia1);
        return visitor;
    }

    public GlobSetAccessor getSetValueAccessor(Field field) {
        return GeneratedGlob.getSetAccessor(field);
    }

    public GlobGetAccessor getGetValueAccessor(Field field) {
        return GeneratedGlob.getGetAccessor(field);
    }

}
