import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Enclosed.class)
public class ImageCompressorTest
{
    public static Collection<Object> data()
    {
        return Arrays.asList(new Object[]{
                "./test-images/test-image1",
                "./test-images/test-image2",
                "./test-images/test-image3",
                "./test-images/test-image4",
                "./test-images/test-image5",
                "./pixel-art/pixel-art1",
                "./pixel-art/pixel-art2",
                "./pixel-art/pixel-art3",
                "./pixel-art/pixel-art4",
                "./pixel-art/pixel-art5",
                "./pixel-art/pixel-art6",
        });
    }

    @RunWith(Parameterized.class)
    public static class ParameterizedTests
    {
        @Parameter
        public String filename;

        @Parameters(name = "{0}")
        public static Collection<Object> data()
        {
            return ImageCompressorTest.data();
        }

        @Test
        public void file()
        {
            ImageCompressorTest.testWithFile(filename);
        }
    }

    public static class SingleTests
    {
        @Test
        public void score()
        {
            int score = 0;

            for (Object f : ImageCompressorTest.data()) {
                Image i = new Image((String) f);
                Drawing d = i.compress();
                score += d.commands.size();
            }

            System.out.println(score);
        }
    }

    public static void testWithFile(String filename)
    {
        Image i = new Image(filename);

        Drawing d = i.compress();

        System.out.println("Nb commands: " + d.commands.size());

        try {
            assertEquals(i.toString(), d.draw().toString());
        } catch (BadCommand e) {
            fail(e.toString());
        }
    }
}
