package com.harasio.savemeapp.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.telephony.SmsManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.harasio.savemeapp.MyFirebaseMessagingService
import com.harasio.savemeapp.PingData
import com.harasio.savemeapp.R
import com.harasio.savemeapp.auth.SignInActivity
import com.harasio.savemeapp.databinding.FragmentHomeBinding
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.AsyncHttpResponseHandler
import com.loopj.android.http.RequestParams
import cz.msebera.android.httpclient.Header
import kotlinx.android.synthetic.main.fragment_home.*
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HomeFragment : Fragment() {
    private lateinit var mAuth: FirebaseAuth
    private lateinit var latLng : LatLng
    private var _binding: FragmentHomeBinding? = null
    private lateinit var myfms: MyFirebaseMessagingService
    private val LOCATION_PERMISSION_REQUEST = 1
    private val SMS_PERMISSION_REQUEST = 1
    private lateinit var currlocation : Location
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private val binding get() = _binding!!
    private var pingList: ArrayList<PingData> = ArrayList()
    private lateinit var bundle: Bundle
    private lateinit var smsManager: SmsManager
    private lateinit var phone: String


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        currlocation = Location("dummyprovider")
        latLng = LatLng(0.0, 0.0)
        fusedLocationProviderClient = activity?.let {
            LocationServices.getFusedLocationProviderClient(it)
        }!!
        if (checkPermission(Manifest.permission.SEND_SMS)) {
        } else {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_REQUEST )
        }
        return root
    }

    private fun checkPermission(permission: String) : Boolean {
        val check = context?.let { ContextCompat.checkSelfPermission(it, permission) }
        return (check == PackageManager.PERMISSION_GRANTED)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
                if (context?.let { ActivityCompat.checkSelfPermission(it, Manifest.permission.ACCESS_FINE_LOCATION) } != PackageManager.PERMISSION_GRANTED && context?.let { ActivityCompat.checkSelfPermission(it, Manifest.permission.ACCESS_COARSE_LOCATION) } != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            } else {
                Toast.makeText(context, "User has not granted location access permission", Toast.LENGTH_LONG).show()
                activity?.finish()
            }
        }
    }

    private fun sendSMS(phone: String, message: String) {
        smsManager = SmsManager.getDefault()
        smsManager.sendTextMessage(phone, null, message, null, null)
        Toast.makeText(context, "SMS SENT", Toast.LENGTH_SHORT).show()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as AppCompatActivity).supportActionBar?.hide()
        mAuth = FirebaseAuth.getInstance()
        val currentUser = mAuth.currentUser
        myfms = MyFirebaseMessagingService()
        bundle = Bundle()

        if(currentUser?.getIdToken(false)?.result?.signInProvider == "google.com")
        {
            binding.tvFullnameHome.text = currentUser.displayName
        }
        else
        {
            val email = currentUser?.email
            val indx = email?.indexOf('@')
            val name = email?.substring(0, indx!!)
            binding.tvFullnameHome.text = name
        }

        getPhoneNumber()

        if (context?.let { ContextCompat.checkSelfPermission(it, Manifest.permission.ACCESS_FINE_LOCATION) } == PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.lastLocation
                .addOnSuccessListener { location :Location ->
                    if(location != null)
                    {
                        currlocation = location
                        latLng  = LatLng(currlocation.latitude,currlocation.longitude)

                        binding.btnPanic.setMainMenu(Color.parseColor("#FF0000"), R.drawable.ic_baseline_panic_24, R.drawable.ic_outline_cancel_24)
                            .addSubMenu(Color.parseColor("#FF0000"), R.drawable.ic_baseline_panic_24)
                            .addSubMenu(Color.parseColor("#FF0000"), R.drawable.ic_baseline_panic_24)
                            .addSubMenu(Color.parseColor("#FF0000"), R.drawable.ic_baseline_panic_24)
                            .addSubMenu(Color.parseColor("#FF0000"), R.drawable.ic_baseline_panic_24)
                            .setOnMenuSelectedListener {
                                /*ini kalo data uid & token yg kita kirim gak sama kaya yg ada di firestore,
                                data lat longnya gak akan kekirim*/
                                val name = tv_fullname_home.text.toString()
                                val uid = mAuth.currentUser?.uid
                                val long =latLng.longitude
                                val lat  = latLng.latitude
                                val token = getDeviceRegistrationToken()
                                var kejahatan = ""
                                val currentDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                val message = "$name sedang dalam bahaya di http://maps.google.com/?q=$lat,$long pada $currentDateTime ."

                                when(it) {
                                    0 -> {
                                        kejahatan = "Kejahatan 1"
                                    }
                                    1 -> {
                                        kejahatan = "Kejahatan 2"
                                    }
                                    2 -> {
                                        kejahatan = "Kejahatan 3"
                                    }
                                    3 -> {
                                        kejahatan = "Kejahatan 4"
                                    }

                                }
                                val client = AsyncHttpClient()
                                val url = "http://159.65.4.250:3000/api/ping/v1/ping"
                                val params = RequestParams()
                                params.put("_id", uid)
                                params.put("kejahatan", kejahatan)
                                params.put("long", long)
                                params.put("lat", lat)
                                params.put("deviceRegistrationToken", token)
                                client.post(url, params ,object : AsyncHttpResponseHandler() {
                                    override fun onSuccess(statusCode: Int, headers: Array<out Header>?, responseBody: ByteArray?) {
                                        Toast.makeText(context, "MANTAP SUKSES PING!", Toast.LENGTH_SHORT).show()
                                    }

                                    override fun onFailure(statusCode: Int, headers: Array<out Header>?, responseBody: ByteArray?, error: Throwable?) {
                                        Toast.makeText(context, "GAGAL PING!!!", Toast.LENGTH_SHORT).show()
                                    }
                                })
                                pingList.add(
                                    PingData(kejahatan, lat.toString(), long.toString(), currentDateTime)
                                )

                                //untuk cek isi data yg dikirim ke server apa aja
                                Toast.makeText(context, uid, Toast.LENGTH_SHORT).show()
                                Toast.makeText(context, kejahatan, Toast.LENGTH_SHORT).show()
                                Toast.makeText(context, long.toString(), Toast.LENGTH_SHORT).show()
                                Toast.makeText(context, lat.toString(), Toast.LENGTH_SHORT).show()
                                Toast.makeText(context, token, Toast.LENGTH_SHORT).show()
                                if (checkPermission(Manifest.permission.SEND_SMS)) {
                                    sendSMS(phone, message)
                                }

                            }
                    }

                }
        }
        else
            activity?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST) }



        binding.signOutBtn.setOnClickListener{
            mAuth.signOut()
            val intent = Intent(context,SignInActivity::class.java)
            startActivity(intent)
            (activity as AppCompatActivity).finish()
        }
    }

    private fun getPhoneNumber() {
        val client = AsyncHttpClient()
        val url = "http://159.65.4.250:3000/api/account/v1/fetch"
        val params = RequestParams()
        params.put("_id", mAuth.currentUser?.uid)
        client.post(url, params ,object : AsyncHttpResponseHandler() {
            override fun onSuccess(statusCode: Int, headers: Array<out Header>?, responseBody: ByteArray) {
                val result = String(responseBody)
                try {
                    val responseObject = JSONObject(result)
                    val dataObject = responseObject.getJSONObject("data")

                    val detZipcode = dataObject.getString("zipcode")

                    phone = detZipcode

                } catch (e: Exception) {
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }

            override fun onFailure(statusCode: Int, headers: Array<out Header>?, responseBody: ByteArray?, error: Throwable?) {
                Toast.makeText(context, "GAGAL Fetch!!!", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun getDeviceRegistrationToken() : String? {
        return context?.let { myfms.getToken(it) }
    }
}