import junit.framework.Assert;
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
    public static Collection<Object[]> data()
    {
        return Arrays.asList(new Object[][]{
                {"./test-images/test-image1", 14},
                {"./test-images/test-image2", 29},
                {"./test-images/test-image3", 200},
                {"./test-images/test-image4", 22},
                {"./test-images/test-image5", 26},
                {"./pixel-art/pixel-art1", 215},
                {"./pixel-art/pixel-art2", 181},
                {"./pixel-art/pixel-art3", 43},
                {"./pixel-art/pixel-art4", 55},
                {"./pixel-art/pixel-art5", 176},
                {"./pixel-art/pixel-art6", 113},
        });
    }

    @RunWith(Parameterized.class)
    public static class CompressSingleTests
    {
        @Parameter(0)
        public String filename;

        @Parameter(1)
        public Integer maxNumberCommands;

        @Parameters(name = "{0}")
        public static Collection<Object[]> data()
        {
            return ImageCompressorTest.data();
        }

        @Test
        public void testWithFile()
        {
            Image i = new Image(filename);

            Drawing d = i.compress();

            int n = d.commands.size();

            System.out.println(filename + "\tNb commands: " + n);

            if (null != maxNumberCommands) {
                assertEquals((int) maxNumberCommands, n);
            }

            try {
                assertEquals(i.toString(), d.draw().toString());
            } catch (BadCommand e) {
                fail(e.toString());
            }
        }
    }

    public static class Compress
    {
        @Test
        public void score()
        {
            int score = 0;

            for (Object[] f : ImageCompressorTest.data()) {
                Image i = new Image((String) f[0]);
                Drawing d = i.compress();
                score += d.commands.size();
            }

            System.out.println(score);
        }
    }
}
