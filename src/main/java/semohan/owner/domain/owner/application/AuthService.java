package semohan.owner.domain.owner.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import semohan.owner.domain.owner.domain.Owner;
import semohan.owner.domain.owner.dto.TemporaryPasswordRequestDto;
import semohan.owner.domain.owner.dto.SignInDto;
import semohan.owner.domain.owner.dto.FindIdVerificationDto;
import semohan.owner.domain.owner.dto.TemporaryPasswordVerificationDto;
import semohan.owner.domain.owner.repository.OwnerRepository;
import semohan.owner.global.exception.CustomException;

import static semohan.owner.global.exception.ErrorCode.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final OwnerRepository ownerRepository;
    private final SmsService smsService;
    private final ValidationService validationService;
    private final RedisService redisService;

    public long signIn(SignInDto signInDto) {
        // username으로 owner 가져오기
        Owner owner = ownerRepository.findOwnerByUsername(signInDto.getUsername()).orElseThrow();

        // 비밀번호 확인
        if(!signInDto.getPassword().equals(owner.getPassword())) {
            throw new CustomException(INVALID_MEMBER);
        }
        return owner.getId();
    }

    public boolean sendVerifySms(String phoneNumber) {
        //수신번호 형태에 맞춰 "-"을 ""로 변환
        String phoneNumberWithoutDashes = phoneNumber.replaceAll("-","");
        String verificationCode = validationService.createCode();
        smsService.sendVerifySms(phoneNumberWithoutDashes, verificationCode);

        //인증코드 유효기간 5분 설정
        redisService.setDataExpire(phoneNumber, verificationCode, 60 * 5L);
        return true;
    }

    public String verifySms(FindIdVerificationDto request) {
        // redis에 저장된 인증코드 꺼내오기
        String verificationCode = redisService.getData(request.getPhoneNumber());

        // 사용자 입력 코드랑 저장된 인증 코드랑 일치 확인
        if (validationService.verifyCode(request.getVerificationCode(), verificationCode)) {
            // phoneNumber로 owner 가져오기
            Owner owner = ownerRepository.findOwnerByPhoneNumber(request.getPhoneNumber()).orElse(null);

            // 일치 유저 없으면 예외
            if (owner == null) {
                throw new CustomException(MEMBER_NOT_FOUND);
            } else {
                redisService.deleteData(request.getPhoneNumber());
                return owner.getUsername(); // 인증 코드 일치, 유저 있으면 redis 삭제 후 아이디 반환
            }
        } else {
            throw new CustomException(INCORRECT_VERIFICATION_CODE);
        }
    }

//    public boolean resetPassword(TemporaryPasswordRequestDto request) {
//        // 아이디로 사용자 조회
//        Owner owner = ownerRepository.findOwnerByUsername(request.getUsername()).orElse(null);
//
//        // 입력한 아이디로 찾은 owner의 휴대전화 번호와 입력한 휴대전화 번호가 일치하는지 확인
//        if (owner != null) {
//            if (owner.getPhoneNumber().equals(request.getPhoneNumber())) {
//                // 비밀번호 재설정
//                owner.setPassword(request.getPassword());
//                ownerRepository.save(owner); // 변경된 비밀번호 저장
//                return true; // 비밀번호 변경 성공
//            }
//        }
//            return false;   // 변경 실패
//    }

    public boolean sendSmsForResetPassword(TemporaryPasswordRequestDto request) {
        // 아이디로 사용자 조회
        Owner owner = ownerRepository.findOwnerByUsername(request.getUsername()).orElse(null);
        if (owner == null) {
            throw new CustomException(MEMBER_NOT_FOUND);
        }
        
        //수신번호 형태에 맞춰 "-"을 ""로 변환
        String phoneNumberWithoutDashes = request.getPhoneNumber().replaceAll("-","");
        String verificationCode = validationService.createCode();
        smsService.sendVerifySms(phoneNumberWithoutDashes, verificationCode);

        //인증코드 유효기간 5분 설정
        redisService.setDataExpire(request.getPhoneNumber(), verificationCode, 60 * 5L);
        return true;
    }

    public boolean sendTempPassword(TemporaryPasswordVerificationDto request) {
        // redis에 저장된 인증코드 꺼내오기
        String verificationCode = redisService.getData(request.getPhoneNumber());

        // 사용자 입력 코드랑 저장된 인증 코드랑 일치 확인
        if (validationService.verifyCode(request.getVerificationCode(), verificationCode)) {
            // 아이디로 사용자 조회
            Owner owner = ownerRepository.findOwnerByUsername(request.getUsername()).orElse(null);
            if (owner == null) {
                throw new CustomException(MEMBER_NOT_FOUND);
            }
            
            // 입력한 아이디로 찾은 owner의 휴대전화 번호와 입력한 휴대전화 번호가 일치하는지 확인
            if (owner.getPhoneNumber().equals(request.getPhoneNumber())) {
                String tempPassword = validationService.createTemporaryPassword();  // 임시 패스워드 생성
                owner.setPassword(tempPassword);
                ownerRepository.save(owner); // 변경된 비밀번호 저장

                //수신번호 형태에 맞춰 "-"을 ""로 변환
                String phoneNumberWithoutDashes = request.getPhoneNumber().replaceAll("-","");
                smsService.sendTemporaryPassword(phoneNumberWithoutDashes, tempPassword);
                redisService.deleteData(request.getPhoneNumber());  // redis 저장 데이터 삭제
                return true; // 임시 비밀번호 문자 전송 성공
            } else {
                throw new CustomException(MISMATCH_PHONENUMBER);    // 저장된 폰번호와 입력된 폰번호 불일치
            }
        } else {
            throw new CustomException(INCORRECT_VERIFICATION_CODE);
        }
    }
}
