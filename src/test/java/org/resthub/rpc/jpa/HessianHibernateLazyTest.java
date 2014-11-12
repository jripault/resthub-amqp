package org.resthub.rpc.jpa;

import org.fest.assertions.api.Assertions;
import org.resthub.rpc.jpa.model.Address;
import org.resthub.rpc.jpa.model.Person;
import org.resthub.rpc.jpa.service.PersonService;
import org.resthub.test.AbstractTransactionalTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testng.annotations.Test;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;

@ContextConfiguration(locations = {"classpath:applicationContext-hessian-jpa.xml", "classpath:applicationContext-hessian-client.xml"})
@ActiveProfiles({ "resthub-jpa", "resthub-pool-bonecp" })
public class HessianHibernateLazyTest extends AbstractTransactionalTest {

	@Inject
    @Named("personService")
    private PersonService personService;
	
	@Resource(name="personServiceProxy")
	private PersonService proxy;
	
	@Test(groups = "hessian-serialization")
	public void test(){
		Person p = this.personService.create(new Person());
		
		Assertions.assertThat(p.getId()).isNotNull();
	}
	
	@Test(groups = "hessian-serialization")
	public void testGetlazyObject(){
		Person p = new Person();
		
		Address a = new Address("42", "test", "test", "test");
		a.setPerson(p);
		a.setPersonSet(p);
		
		p.getAddresses().add(a);
		p.getAddressesSet().add(a);

        p.getMyAdresses().put("home", a);

		p = this.proxy.create(p);
		
		Person p2 = this.proxy.findById(p.getId());
		Assertions.assertThat(p2.getAddresses().size()).isEqualTo(0);
		Assertions.assertThat(p2.getAddressesSet().size()).isEqualTo(0);
        Assertions.assertThat(p2.getMyAdresses().size()).isEqualTo(0);
		
		Person p3 = this.proxy.findByIdWithAddress(p.getId());
		Assertions.assertThat(p3.getAddresses().size()).isEqualTo(1);
		Assertions.assertThat(p3.getAddressesSet().size()).isEqualTo(1);
        Assertions.assertThat(p3.getMyAdresses().size()).isEqualTo(1);
	}
}
