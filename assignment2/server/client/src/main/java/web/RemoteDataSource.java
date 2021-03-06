package web;

import com.google.gson.GsonBuilder;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

import java.io.InputStream;
import java.time.Duration;
import java.util.List;

/**
 * Intelligent remote data source adapter that handles all the logic
 * for invoking Flight Listing App (FLApp) endpoint methods and
 * managing tokens using the OAuth PKCE authorization in conjunction
 * with a WebView.
 * <p>
 * The @Singleton annotation is used to mark the class as Singleton
 * Session Bean, i.e., there's just one instance shared by other
 * components in this app.
 */
public class RemoteDataSource {
    /**
     * Http request timeouts.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(15);

    /**
     * API instance.
     */
    private static ImageTransformApi api = null;

    /**
     * RxJava adapter needed to obtain info from the server.
     */
    private static final RxJava3CallAdapterFactory rxAdapter =
            RxJava3CallAdapterFactory.createWithScheduler(Schedulers.io());

    /**
     * The constructor initializes the Retrofit API instance.
     */
    public RemoteDataSource(String baseUrl) {
        api = buildApi(baseUrl);
    }

    public void transformImage() {
        try {
            // Build list of transform names for API call.
            List<String> transformNames = List.of("GrayScaleTransform", "SepiaTransform", "TintTransform");
            String fileName = "__notag__-www.dre.vanderbilt.edu%252F%257Eschmidt%252Fimgs%252Fimgs2%252Fdougnew.jpg";

            InputStream inputStream =
                    RemoteDataSource.class.getResourceAsStream("/" + fileName);

            // Get the image bytes from the input stream.
            byte[] bytes = IOUtils.toBytes(inputStream);

            // Build a multipart request body containing the image bytes.
            RequestBody requestBody =
                    RequestBody.create(bytes, MediaType.parse("multipart/form-data"));

            // Create the request part for the image bytes request body.
            MultipartBody.Part imagePart =
                    MultipartBody.Part.createFormData("image", fileName, requestBody);

            Single<List<TransformedImage>> single =
                    api.applyTransforms(transformNames, imagePart);

            List<TransformedImage> transformedImages = single.blockingGet();

            for (TransformedImage transformedImage : transformedImages) {
                System.out.println(transformedImage.getTransformName());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return An OkHttpClient that supports token authentication.
     */
    private static OkHttpClient buildHttpClient() {
        HttpLoggingInterceptor httpLoggingInterceptor =
                new HttpLoggingInterceptor();
        httpLoggingInterceptor
                .setLevel(HttpLoggingInterceptor.Level.HEADERS);

        return new OkHttpClient
                .Builder()
                .addInterceptor(httpLoggingInterceptor)
                .connectTimeout(DEFAULT_TIMEOUT)
                .readTimeout(DEFAULT_TIMEOUT)
                .writeTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    private static ImageTransformApi buildApi(String baseUrl) {
        return new Retrofit
                .Builder()
                .baseUrl(baseUrl)
                .client(buildHttpClient())
                .addCallAdapterFactory(rxAdapter)
                .addConverterFactory(GsonConverterFactory
                        .create(new GsonBuilder()
                                .setDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ")
                                .setLenient()
                                .create()))
                .build()
                .create(ImageTransformApi.class);
    }

    public interface ImageTransformApi {
        /**
         * Path for the image filter endpoint.
         */
        String APPLY_TRANSFORMS = "/apply-transforms";

        @Multipart
        @POST(APPLY_TRANSFORMS)
        Single<List<TransformedImage>> applyTransforms(
                @Query("transforms") List<String> transforms,
                @Part MultipartBody.Part image);
    }
}