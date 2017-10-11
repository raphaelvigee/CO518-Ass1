import org.junit.Test;

import java.util.ArrayList;
import java.util.Objects;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public class ImageCompressorTest
{
    @Test
    public void image1()
    {
        testWithFile("./test-images/test-image1");
    }

    @Test
    public void image2()
    {
        testWithFile("./test-images/test-image2");
    }

    @Test
    public void image3()
    {
        testWithFile("./test-images/test-image3");
    }

    @Test
    public void image4()
    {
        testWithFile("./test-images/test-image4");
    }

    @Test
    public void image5()
    {
        testWithFile("./test-images/test-image5");
    }

    private void testWithFile(String filename) {
        Image i = new Image(filename);

        Drawing d = i.compress();

        System.out.println("Nb commands: " + d.commands.size());

        try {
            assertEquals(i.toString(), d.draw().toString());
        } catch(BadCommand e) {
            fail(e.toString());
        }
    }
}
