package net.corda.samples.carinsurance.schema

//4.6 changes
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import org.hibernate.annotations.Type
import java.io.Serializable
import java.util.*
import javax.persistence.*

/**
 * The family of schemas for IOUState.
 */
object InsuranceSchema

/**
 * An IOUState schema.
 */
object InsuranceSchemaV1 : MappedSchema(
        schemaFamily = InsuranceSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentClaim::class.java, PersistentInsurance::class.java, PersistentVehicle::class.java)) {

    override val migrationResource: String?
        get() = "insurance.changelog-master"

    @Entity
    @Table(name = "claim_detail")
    class PersistentClaim(
            @Id @Column(name = "id")
            @Type(type = "uuid-char")
            val uuid: UUID,

            @Column(name = "claim_number")
            var claimNumber: String,

            @Column(name = "claim_description")
            var claimDescription: String,

            @Column(name = "claim_amount")
            var claimAmount: Int
    ) {
        // Default constructor required by hibernate.
        constructor(claimNumber: String,claimDescription: String,claimAmount: Int) : this(
                UUID.randomUUID(), claimNumber,claimDescription,claimAmount)

        constructor() : this(UUID.randomUUID(), "", "", 0)
    }

    @Entity
    @Table(name = "vehicle_detail")
    class PersistentVehicle(
            @Id @Column(name = "id")
            @Type(type = "uuid-char")
            val uuid: UUID,

            @Column(name = "registration_number")
            val registrationNumber: String,

            @Column(name = "chassis_number")
            val chasisNumber: String,

            @Column(name = "make")
            val make: String,

            @Column(name = "model")
            val model: String,

            @Column(name = "variant")
            val variant: String,

            @Column(name = "color")
            val color: String,

            @Column(name = "fuel_type")
            val fuelType: String
    ) {
        // Default constructor required by hibernate.
        constructor(registrationNumber: String, chasisNumber: String, make: String, model: String, variant: String, color: String, fuelType: String) : this(
                UUID.randomUUID(), registrationNumber, chasisNumber, make, model, variant, color, fuelType)

        constructor() : this(UUID.randomUUID(), "", "", "", "", "", "", "")
    }


    @Entity
    @Table(name = "insurance_detail")
    class PersistentInsurance(
            @Column(name = "policy_number")
            val policyNumber: String,
            @Column(name = "insured_value")
            val insuredValue: Long,
            @Column(name = "duration")
            val duration: Int,
            @Column(name = "premium")
            val premium: Int,
            @OneToOne(cascade = [CascadeType.PERSIST])
            @JoinColumns(JoinColumn(name = "id", referencedColumnName = "id"), JoinColumn(name = "registration_number", referencedColumnName = "registration_number"))
            val vehicle: PersistentVehicle?,
            @OneToMany(cascade = [CascadeType.PERSIST])
            @JoinColumns(JoinColumn(name = "output_index", referencedColumnName = "output_index"), JoinColumn(name = "transaction_id", referencedColumnName = "transaction_id"))
            val claims: List<PersistentClaim>
    ) : PersistentState(), Serializable {
        constructor() : this("", 0, 0, 0, PersistentVehicle(), listOf(PersistentClaim()))
    }
}
